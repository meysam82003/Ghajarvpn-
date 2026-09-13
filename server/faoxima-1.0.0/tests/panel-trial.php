<?php
require __DIR__ . '/../overlay/api/lib/PanelTrial.php';
$pdo = new PDO(getenv('TRIAL_TEST_DSN'), getenv('TRIAL_TEST_USER') ?: 'root', getenv('TRIAL_TEST_PASSWORD') ?: '', [PDO::ATTR_ERRMODE => PDO::ERRMODE_EXCEPTION]);
$setting = ['limit_usertest_all' => 1];
if (($argv[1] ?? '') === '--claim') {
    $id = GhajarPanelTrial::reserve(['id' => 900, 'agent' => 'f'], 'a', $setting);
    echo $id === null ? 'blocked' : 'reserved'; exit;
}
function check($condition, $message) { if (!$condition) throw new RuntimeException($message); }
$pdo->exec("CREATE TABLE marzban_panel (code_panel VARCHAR(191) PRIMARY KEY, name_panel VARCHAR(191), status VARCHAR(32), TestAccount VARCHAR(32), agent VARCHAR(32), hide_user TEXT) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
$pdo->exec("CREATE TABLE invoice (id_user BIGINT, Service_location VARCHAR(191), name_product VARCHAR(191), Status VARCHAR(32)) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4");
$q = $pdo->prepare('INSERT INTO marzban_panel VALUES (?,?,?,?,?,?)');
foreach ([['a','Panel A','active','ONTestAccount','all',null], ['b','Panel B','active','ONTestAccount','all',null], ['off','Disabled','inactive','ONTestAccount','all',null], ['no-test','No Test','active','OFFTestAccount','all',null], ['hidden','Hidden','active','ONTestAccount','all','[1]'], ['agent','Agent','active','ONTestAccount','vip',null]] as $row) $q->execute($row);
$user = ['id'=>1,'agent'=>'f', 'limit_usertest'=>0];
check(array_column(GhajarPanelTrial::panels($user,$setting),'code_panel') === ['a','b'], 'Inactive, hidden and wrong-tier panels must be excluded');
check(GhajarPanelTrial::reserve($user,'off',$setting) === null, 'Direct request cannot use a disabled panel');
check(GhajarPanelTrial::reserve($user,'no-test',$setting) === null, 'Test disabled');
$a = GhajarPanelTrial::reserve($user,'a',$setting); check(is_string($a),'First trial on A'); GhajarPanelTrial::finish($a);
check(GhajarPanelTrial::reserve($user,'a',$setting) === null,'Second trial on A blocked across clients');
$b = GhajarPanelTrial::reserve($user,'b',$setting); check(is_string($b),'A must not consume B');
GhajarPanelTrial::release($b); GhajarPanelTrial::release($b);
check(is_string(GhajarPanelTrial::reserve($user,'b',$setting)), 'Confirmed failure refunds exactly once');
check(GhajarPanelTrial::reserve($user,'b',$setting) === null,'Double refund must not grant an extra trial');
$other=['id'=>2,'agent'=>'f'];check(is_string(GhajarPanelTrial::reserve($other,'a',$setting)),'Independent user');
$pdo->exec("INSERT INTO invoice VALUES (3,'Panel A','سرویس تست','active'), (3,'Panel B','سرویس تست','Unsuccessful')");
$history=['id'=>3,'agent'=>'f'];check(GhajarPanelTrial::reserve($history,'a',$setting) === null,'Existing successful test is migrated');
check(is_string(GhajarPanelTrial::reserve($history,'b',$setting)),'Failed historical test does not consume quota');
$pdo->exec("UPDATE marzban_panel SET status='inactive' WHERE code_panel='b'");
check(GhajarPanelTrial::reserve(['id'=>4,'agent'=>'f'],'b',$setting) === null,'Panel disabled after selector fetch is rejected');
$two=['limit_usertest_all'=>2];$u=['id'=>5,'agent'=>'f'];
check(is_string(GhajarPanelTrial::reserve($u,'a',$two)),'Cap two: first');
check(is_string(GhajarPanelTrial::reserve($u,'a',$two)),'Cap two: second');
check(GhajarPanelTrial::reserve($u,'a',$two) === null,'Cap two: third blocked');
// Independent PHP workers simulate concurrent Telegram and mini-app requests.
$workers=[];
for ($i=0;$i<6;$i++) {
    $proc=proc_open([PHP_BINARY,__FILE__,'--claim'], [0=>['pipe','r'],1=>['pipe','w'],2=>['pipe','w']],$pipes);
    fclose($pipes[0]);$workers[]=[$proc,$pipes];
}
$wins=0;
foreach($workers as [$proc,$pipes]) {
    $out=stream_get_contents($pipes[1]);$err=stream_get_contents($pipes[2]);fclose($pipes[1]);fclose($pipes[2]);
    check(proc_close($proc)===0,'Concurrent worker failed: '.$err);
    if($out==='reserved')$wins++;
}
check($wins===1,'Concurrent requests must have exactly one winner');
echo "PASS: per-panel caps, inactive panels, history migration, refunds and concurrent clients\n";
