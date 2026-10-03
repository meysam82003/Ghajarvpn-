#!/usr/bin/env python3
"""Host-only source checks. Android classes are references, not an Android runtime test."""
from pathlib import Path
import subprocess, argparse
p=argparse.ArgumentParser();p.add_argument('--dependencies',type=Path,required=True);a=p.parse_args()
r=Path(__file__).resolve().parents[2];k=a.dependencies.resolve();work=k/'remaining-check';work.mkdir(exist_ok=True)
cp=':'.join(str(k/x) for x in ['android-all-15.jar','json-20240303.jar','kotlin-stdlib-2.1.0.jar','annotations-13.0.jar','kotlinx-coroutines-core-jvm-1.6.4.jar','junit-4.13.2.jar','hamcrest-core-1.3.jar'])
def call(cmd):subprocess.run(list(map(str,cmd)),check=True)
build=work/'BuildConfig.java';build.write_text('package net.ghajar.plugin.mihomo; public final class BuildConfig { public static final String HOST_CERTIFICATE=""; public static final int VERSION_CODE=1; }')
call(['java','--add-modules','jdk.compiler','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',cp,'-d',work,*sorted((r/'plugin-api/src/main/java').rglob('*.java')),build,r/'plugins/mihomo/src/main/java/net/ghajar/plugin/mihomo/MihomoService.java'])
b=r/'app/src/main/java/net/gozar/app'
def kotlin(out, sources, extra=''):
 call(['java','-cp',str(k/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',cp+':'+str(work)+extra,'-d',work/out,*sources])
kotlin('renewal.jar',[b/'GhajarRenewal.kt',r/'app/src/test/java/net/gozar/app/GhajarRenewalTest.kt'])
call(['java','-cp',str(work/'renewal.jar')+':'+cp,'org.junit.runner.JUnitCore','net.gozar.app.GhajarRenewalTest'])
stub=work/'log.kt';stub.write_text('''package net.gozar.app
object GhajarLog {
 fun i(tag:String,msg:String){}; fun w(tag:String,msg:String){}
 fun e(tag:String,msg:String, e:Throwable?=null){}; fun redact(s:String)=s
}
''')
kotlin('chain-runtime.jar',[b/x for x in ['ChainSession.kt','ProcessCleanup.kt','AetherTorPolicy.kt','AetherTorRuntime.kt','Aethercontroller.kt','Torcontroller.kt','engine/SingBoxController.kt','engine/DnsTunnelTuning.kt','engine/SidecarRunner.kt','engine/DnsTunnelPrefs.kt']]+[stub],':'+str(k/'phase3-tests.jar'))
# Parse every changed Kotlin file, including Compose/service wiring. This is syntax only.
java=work/'Syntax.java';java.write_text('''import java.nio.file.*; import org.jetbrains.kotlin.cli.jvm.compiler.*; import org.jetbrains.kotlin.config.*; import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer; import org.jetbrains.kotlin.com.intellij.psi.*; import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil; import org.jetbrains.kotlin.psi.*;
public class Syntax { public static void main(String[] args) throws Exception { var d=Disposer.newDisposable(); try { var env=KotlinCoreEnvironment.createForProduction(d,new CompilerConfiguration(),EnvironmentConfigFiles.JVM_CONFIG_FILES); var factory=new KtPsiFactory(env.getProject(),false); for(String p:args){var file=factory.createFile(Path.of(p).getFileName().toString(),Files.readString(Path.of(p)));var errors=PsiTreeUtil.collectElementsOfType(file,PsiErrorElement.class);if(!errors.isEmpty())throw new Exception(p+errors.toString());}System.out.println("Kotlin syntax accepted: "+args.length); }finally{Disposer.dispose(d);} } }
''')
call(['java','--add-modules','jdk.compiler','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',':'.join(map(str,k.glob('*.jar'))),'-d',work,java])
files=subprocess.check_output(['git','ls-files','-m','-o','--exclude-standard'],cwd=r,text=True).splitlines();files=[r/f for f in files if f.endswith('.kt')]
call(['java','-cp',str(work)+':'+str(k/'*'),'Syntax',*files])
print('API + Mihomo Java, chain controller/runtime Kotlin type checks passed; no APK produced.')
