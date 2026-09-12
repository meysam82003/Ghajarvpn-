<?php
require_once __DIR__.'/PurchaseSettlement.php';

/** Shared entry point for verified callbacks, receipt approval and retries. */
function DirectPayment($order_id, $image='images.jpg') {
    global $pdo;
    $report=select('Payment_report','*','id_order',$order_id,'select');
    if(!is_array($report))return;
    if(!str_starts_with((string)($report['id_invoice']??''),'getconfigafterpay|')) {
        GhajarDirectPaymentLegacy($order_id,$image);return;
    }
    if(($report['payment_Status']??'')!=='paid')return;
    $key='gateway:'.$order_id;
    if(!GhajarPurchaseSettlement::lock($key))return;
    try {
        $state=GhajarPurchaseSettlement::get($key);
        if($state && in_array($state['state'],['delivered','refunded'],true))return;
        $username=explode('|',(string)$report['id_invoice'],2)[1];
        $q=$pdo->prepare('SELECT * FROM invoice WHERE id_user=? AND username=? LIMIT 1');$q->execute([$report['id_user'],$username]);$invoice=$q->fetch(PDO::FETCH_ASSOC);
        if(!$state && $invoice && strtolower((string)$invoice['Status'])==='active')return;
        $state=GhajarPurchaseSettlement::fund($key,(string)$report['id_user'],(string)($invoice['id_invoice']??''),(float)($invoice['price_product']??0),(float)$report['price']);
        if($state['state']==='refunded'){
            if(function_exists('sendmessage'))sendmessage($report['id_user'],'سرویس تحویل نشد؛ مبلغ پرداختی در کیف پول شما قرار گرفت.',null,'HTML');return;
        }
        GhajarDirectPaymentLegacy($order_id,$image);
    } finally { GhajarPurchaseSettlement::unlock($key); }
}
