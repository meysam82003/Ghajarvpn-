<?php
require_once __DIR__.'/PurchaseSettlement.php';
/** Reconcile uncertain requests without creating another remote account. */
function ghajar_recover_purchases($manager): void {
    global $pdo;
    if(!$manager)return;
    GhajarPurchaseSettlement::schema();
    $q=$pdo->prepare("SELECT * FROM ghajar_purchase_settlement WHERE state='charged' AND updated_at<? ORDER BY updated_at LIMIT 3");$q->execute([time()-300]);
    foreach($q->fetchAll(PDO::FETCH_ASSOC) as $row) {
        $key=$row['purchase_key'];
        if(!GhajarPurchaseSettlement::lock($key))continue;
        try {
            $fresh=GhajarPurchaseSettlement::get($key);if(($fresh['state']??'')!=='charged')continue;
            $s=$pdo->prepare('SELECT * FROM invoice WHERE id_user=? AND id_invoice=?');$s->execute([$row['user_id'],$row['invoice_id']]);$invoice=$s->fetch(PDO::FETCH_ASSOC);
            $refund=!$invoice;
            if($invoice) {
                if(!empty($invoice['user_info']) && strtolower((string)$invoice['Status'])==='active'){GhajarPurchaseSettlement::delivered($key);continue;}
                $remote=$manager->DataUser($invoice['Service_location'],$invoice['username']);
                if(is_array($remote)&&($remote['username']??'')===$invoice['username']){GhajarPurchaseSettlement::delivered($key);continue;}
                $refund=GhajarPurchaseSettlement::missing($remote);
            }
            if($refund && GhajarPurchaseSettlement::refund($key) && function_exists('sendmessage'))sendmessage($row['user_id'],'سرویس ساخته نشد؛ مبلغ پرداختی به کیف پول شما برگشت داده شد.',null,'HTML');
        }catch(Throwable $e){error_log('[Ghajar purchase reconciliation] '.$e->getMessage());}
        finally{GhajarPurchaseSettlement::unlock($key);}
    }
}
