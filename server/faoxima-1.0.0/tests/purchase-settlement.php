<?php
require __DIR__.'/../overlay/lib/PurchaseSettlement.php';
$pdo=new PDO(getenv('TRIAL_TEST_DSN'),getenv('TRIAL_TEST_USER')?:'root',getenv('TRIAL_TEST_PASSWORD')?:'',[PDO::ATTR_ERRMODE=>PDO::ERRMODE_EXCEPTION]);
function check($v,$m){if(!$v)throw new RuntimeException($m);}
function balance($id){global $pdo;$q=$pdo->prepare('SELECT Balance FROM user WHERE id=?');$q->execute([$id]);return (float)$q->fetchColumn();}
if(($argv[1]??'')==='--refund'){echo GhajarPurchaseSettlement::refund('wallet:race')?'credited':'unchanged';exit;}
$pdo->exec('DROP TABLE invoice');
$pdo->exec("CREATE TABLE user (id BIGINT PRIMARY KEY, Balance DECIMAL(20,4) NOT NULL) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE invoice (id_invoice VARCHAR(191) PRIMARY KEY,id_user BIGINT,Status VARCHAR(32)) ENGINE=InnoDB");
$pdo->exec("CREATE TABLE Payment_report (id_order VARCHAR(191) PRIMARY KEY,id_user BIGINT,price DECIMAL(20,4),payment_Status VARCHAR(24),dec_not_confirmed TEXT) ENGINE=InnoDB");
$pdo->exec("INSERT INTO user VALUES(1,100),(2,40),(3,20),(4,100),(5,100),(6,100)");
$pdo->exec("INSERT INTO invoice VALUES('a',1,'pending'),('b',2,'unpaid'),('c',3,'unpaid'),('race',4,'pending'),('done',5,'pending'),('rollback',6,'pending')");
$s=GhajarPurchaseSettlement::fund('wallet:a','1','a',75);check($s['state']==='charged'&&balance(1)===25.0,'wallet charge');
check(GhajarPurchaseSettlement::refund('wallet:a')&&balance(1)===100.0,'full refund');check(!GhajarPurchaseSettlement::refund('wallet:a')&&balance(1)===100.0,'no double refund');
$pdo->exec("INSERT INTO Payment_report VALUES('b',2,60,'paid',NULL),('c',3,60,'paid',NULL),('missing',1,12,'paid',NULL)");
GhajarPurchaseSettlement::fund('gateway:b','2','b',100,60);check(balance(2)===0.0,'mixed wallet plus gateway');GhajarPurchaseSettlement::refund('gateway:b');check(balance(2)===100.0,'preserve old wallet and paid amount');
GhajarPurchaseSettlement::fund('gateway:b','2','b',100,60);check(balance(2)===100.0,'replayed callback must not credit again');
$s=GhajarPurchaseSettlement::fund('gateway:c','3','c',100,60);check($s['state']==='refunded'&&balance(3)===80.0,'wallet spent meanwhile: keep paid amount, no negative purchase');
GhajarPurchaseSettlement::fund('gateway:missing','1','',0,12);check(balance(1)===112.0,'missing invoice deposits paid amount');
GhajarPurchaseSettlement::fund('wallet:done','5','done',25);GhajarPurchaseSettlement::delivered('wallet:done');check(!GhajarPurchaseSettlement::refund('wallet:done')&&balance(5)===75.0,'delivered service cannot refund');
GhajarPurchaseSettlement::fund('wallet:race','4','race',37.25);
$workers=[];for($i=0;$i<6;$i++){$p=proc_open([PHP_BINARY,__FILE__,'--refund'],[0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);fclose($pipes[0]);$workers[]=[$p,$pipes];}
$wins=0;foreach($workers as [$p,$pipes]){$out=stream_get_contents($pipes[1]);$err=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);check(proc_close($p)===0,$err);if($out==='credited')$wins++;}check($wins===1&&balance(4)===100.0,'concurrent refund exactly once');
try{GhajarPurchaseSettlement::fund('gateway:missing','2','',0,12);throw new Exception('owner accepted');}catch(RuntimeException $e){check(balance(2)===100.0,'owner mismatch leaves balances unchanged');}
check(GhajarPurchaseSettlement::lock('gateway:b'),'advisory lock');GhajarPurchaseSettlement::unlock('gateway:b');
$manager=new class {function createUser(...$a){throw new RuntimeException('response lost');}function DataUser(...$a){return ['username'=>'same','links'=>['vless://test']];}};
check(GhajarPurchaseSettlement::create($manager,'panel','product','same',[])['username']==='same','lost response recovers same service rather than refund');
echo "PASS: wallet and gateway refunds, partial funding, missing invoice, delivered service, ownership and six concurrent callbacks\n";
