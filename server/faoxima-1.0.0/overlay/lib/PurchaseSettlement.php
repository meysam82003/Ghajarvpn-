<?php
declare(strict_types=1);

/** Durable funding/refunds. All monetary mutations and their receipts use the same PDO transaction. */
final class GhajarPurchaseSettlement
{
    private static function db(): PDO { global $pdo; if (!($pdo instanceof PDO)) throw new RuntimeException('Database unavailable'); return $pdo; }
    public static function schema(): void {
        static $ready=false; if ($ready) return;
        self::db()->exec("CREATE TABLE IF NOT EXISTS ghajar_purchase_settlement (
            purchase_key VARCHAR(191) PRIMARY KEY, user_id BIGINT NOT NULL,
            invoice_id VARCHAR(191) NOT NULL, paid_amount DECIMAL(20,4) NOT NULL DEFAULT 0,
            charged_amount DECIMAL(20,4) NOT NULL DEFAULT 0, state VARCHAR(24) NOT NULL,
            updated_at BIGINT NOT NULL
        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4"); $ready=true;
    }
    /** Prevent two callbacks from provisioning the same order while allowing unrelated purchases. */
    public static function lock(string $key): bool {
        self::schema();$q=self::db()->prepare('SELECT GET_LOCK(?, 0)');$q->execute(['ghajar-buy:'.substr(hash('sha256',$key),0,40)]);return (int)$q->fetchColumn()===1;
    }
    public static function unlock(string $key): void {
        $q=self::db()->prepare('SELECT RELEASE_LOCK(?)');$q->execute(['ghajar-buy:'.substr(hash('sha256',$key),0,40)]);
    }
    public static function get(string $key): ?array {
        self::schema();$q=self::db()->prepare('SELECT * FROM ghajar_purchase_settlement WHERE purchase_key=?');$q->execute([$key]);return $q->fetch(PDO::FETCH_ASSOC) ?: null;
    }
    /** A verified gateway payment is credited first, then the invoice is charged.
     * If funding is insufficient or no invoice exists, the payment remains in the wallet.
     */
    public static function fund(string $key, string $uid, string $invoice, float $price, float $paid=0, float $allowNegative=0): array {
        if (!is_finite($price)||!is_finite($paid)||$price<0||$paid<0) throw new InvalidArgumentException('Invalid amount');
        self::schema();$db=self::db();$db->beginTransaction();
        try {
            if (str_starts_with($key,'gateway:')) {
                $q=$db->prepare('SELECT * FROM Payment_report WHERE id_order=? FOR UPDATE');$q->execute([substr($key,8)]);$payment=$q->fetch(PDO::FETCH_ASSOC);
                if(!$payment || $payment['payment_Status']!=='paid' || (string)$payment['id_user']!==$uid || (float)$payment['price']!==$paid) throw new RuntimeException('Payment not verified');
                $previous=self::get($key);
                if(!$previous && preg_match('/auto-refund|service-created/i',(string)($payment['dec_not_confirmed']??''))) throw new RuntimeException('Payment already settled by previous version');
                $q=$db->prepare("UPDATE Payment_report SET dec_not_confirmed=CONCAT(COALESCE(dec_not_confirmed,''),' [ghajar-settlement]') WHERE id_order=? AND COALESCE(dec_not_confirmed,'') NOT LIKE '%ghajar-settlement%'");$q->execute([substr($key,8)]);
            }
            $q=$db->prepare('SELECT * FROM user WHERE id=? FOR UPDATE');$q->execute([$uid]);$user=$q->fetch(PDO::FETCH_ASSOC);
            if (!$user) throw new RuntimeException('Purchase user missing');
            $q=$db->prepare('SELECT * FROM ghajar_purchase_settlement WHERE purchase_key=? FOR UPDATE');$q->execute([$key]);$existing=$q->fetch(PDO::FETCH_ASSOC);
            if ($existing) { if ((string)$existing['user_id']!==$uid) throw new RuntimeException('Purchase owner mismatch'); $db->commit();return $existing; }
            if ($paid>0) { $q=$db->prepare('UPDATE user SET Balance=Balance+? WHERE id=?');$q->execute([$paid,$uid]); }
            $charged=0.0;$state='refunded';
            if ($invoice!=='' && $price>0) {
                $q=$db->prepare('UPDATE user SET Balance=Balance-? WHERE id=? AND Balance+? >= ?');
                $q->execute([$price,$uid,max(0,$allowNegative),$price]);
                if($q->rowCount()===1){$charged=$price;$state='charged';}
            } elseif ($invoice!=='' && $price==0) $state='charged';
            $q=$db->prepare('INSERT INTO ghajar_purchase_settlement VALUES (?,?,?,?,?,?,?)');
            $q->execute([$key,$uid,$invoice,$paid,$charged,$state,time()]);
            if ($state==='refunded') { self::markInvoice($db,$uid,$invoice,'refunded'); self::markPayment($db,$key,'auto-refund'); }
            $db->commit();return self::get($key);
        } catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
    }
    private static function markPayment(PDO $db,string $key,string $state): void {
        if(!str_starts_with($key,'gateway:'))return;
        $q=$db->prepare("UPDATE Payment_report SET dec_not_confirmed=CONCAT(COALESCE(dec_not_confirmed,''),?) WHERE id_order=?");$q->execute([' ['.$state.': ghajar]',substr($key,8)]);
    }
    private static function markInvoice(PDO $db,string $uid,string $invoice,string $status): void {
        if($invoice==='')return;$q=$db->prepare('UPDATE invoice SET Status=? WHERE id_user=? AND id_invoice=?');$q->execute([$status,$uid,$invoice]);
    }
    /** Returns true only for the transaction that actually returned a charged amount. */
    public static function refund(string $key): bool {
        $db=self::db();$db->beginTransaction();
        try {
            $q=$db->prepare('SELECT * FROM ghajar_purchase_settlement WHERE purchase_key=? FOR UPDATE');$q->execute([$key]);$r=$q->fetch(PDO::FETCH_ASSOC);
            if(!$r || $r['state']!=='charged'){$db->commit();return false;}
            $q=$db->prepare('UPDATE user SET Balance=Balance+? WHERE id=?');$q->execute([$r['charged_amount'],$r['user_id']]);
            if((float)$r['charged_amount']>0 && $q->rowCount()!==1)throw new RuntimeException('Refund user missing');
            $q=$db->prepare("UPDATE ghajar_purchase_settlement SET state='refunded',updated_at=? WHERE purchase_key=?");$q->execute([time(),$key]);
            self::markInvoice($db,(string)$r['user_id'],(string)$r['invoice_id'],'refunded');self::markPayment($db,$key,'auto-refund');
            $db->commit();return true;
        }catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
    }
    public static function delivered(string $key): bool {
        $db=self::db();$db->beginTransaction();
        try {
            $q=$db->prepare('SELECT * FROM ghajar_purchase_settlement WHERE purchase_key=? FOR UPDATE');$q->execute([$key]);$r=$q->fetch(PDO::FETCH_ASSOC);
            if(!$r || $r['state']!=='charged'){$db->commit();return $r && $r['state']==='delivered';}
            self::markInvoice($db,(string)$r['user_id'],(string)$r['invoice_id'],'active');self::markPayment($db,$key,'service-created');
            $q=$db->prepare("UPDATE ghajar_purchase_settlement SET state='delivered',updated_at=? WHERE purchase_key=?");$q->execute([time(),$key]);$db->commit();return true;
        }catch(Throwable $e){if($db->inTransaction())$db->rollBack();throw $e;}
    }
    /** A transport exception is not proof of failure: recover the same username before retrying/refunding. */
    public static function create($manager, string $panel, string $product, string $username, array $payload): array {
        try { $result=$manager->createUser($panel,$product,$username,$payload); }
        catch(Throwable $e){ $result=['msg'=>'Provisioning response unavailable']; }
        if(is_array($result)&&!empty($result['username']))return $result;
        try{$existing=$manager->DataUser($panel,$username);if(is_array($existing)&&($existing['username']??'')===$username){
            if(empty($existing['configs'])&&!empty($existing['links']))$existing['configs']=is_array($existing['links'])?$existing['links']:explode("\n",$existing['links']);
            return $existing;
        }}catch(Throwable $e){throw new RuntimeException('Service outcome pending reconciliation',0,$e);}
        // Unknown outcomes remain charged for retry; a structured panel rejection is definitive.
        if(!is_array($result) || !array_key_exists('msg',$result) || ($result['msg']??'')==='Provisioning response unavailable')
            throw new RuntimeException('Service outcome pending reconciliation');
        return $result;
    }
}
