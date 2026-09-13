<?php
declare(strict_types=1);
require_once __DIR__.'/BaseHandler.php';
require_once __DIR__.'/../../lib/DirectPurchase.php';
final class PaymentFallbackWalletHandler extends BaseHandler {
    public function handle(): void {
        $this->requireMethod('POST');
        $order=FaoximaInput::string($this->data,'order_id');
        $report=FaoximaDb::fetchOne('SELECT * FROM Payment_report WHERE id_order=:o AND id_user=:u', [':o'=>$order,':u'=>$this->user['id']]);
        if(!$report)FaoximaResponse::notFound('Payment not found');
        if(($report['payment_Status']??'')!=='paid')FaoximaResponse::fail(409,'پرداخت هنوز تأیید نشده است.');
        if(!str_starts_with((string)$report['id_invoice'],'getconfigafterpay|'))FaoximaResponse::fail(409,'این پرداخت خرید سرویس نیست.');
        global $ManagePanel;$ManagePanel=$ManagePanel??new ManagePanel();
        try{DirectPayment($order);}catch(Throwable $e){FaoximaLogger::exception($e,'Purchase reconciliation pending');}
        $state=GhajarPurchaseSettlement::get('gateway:'.$order);
        $refunded=($state['state']??'')==='refunded';
        FaoximaResponse::ok(['wallet_credited'=>$refunded,'stage'=>$refunded?'wallet_refunded':($state['state']??'pending'),
            'message'=>$refunded?'مبلغ پرداختی در کیف پول شما قرار گرفت.':'نتیجه ساخت سرویس در حال بررسی است.']);
    }
}
