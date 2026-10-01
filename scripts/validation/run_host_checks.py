#!/usr/bin/env python3
"""Isolated actual-source JVM tests; unrelated Android/native collaborators are explicit fixtures."""
from pathlib import Path
import subprocess
import argparse
parser=argparse.ArgumentParser();parser.add_argument('--dependencies',type=Path,required=True);args=parser.parse_args()
root=Path(__file__).resolve().parents[2]; k=args.dependencies.resolve()
fixtures=Path(__file__).resolve().parent/'host-fixtures'
# Plain javac check of API contracts only; no APK, Gradle, native build or signing.
(k/'sdk-check').mkdir(exist_ok=True)
subprocess.run(['java','--add-modules','jdk.compiler','-m','jdk.compiler/com.sun.tools.javac.Main','-cp',str(k/'android-all-15.jar')+':'+str(k/'json-20240303.jar'),'-d',str(k/'sdk-check'),
 str(root/'plugin-api/src/main/java/net/gozar/plugin/api/ShadowQuicProfile.java')],check=True)
b=root/'app/src/main/java/net/gozar/app'
sources=[b/p for p in ['EngineSettings.kt','freecfg/FreeSourcePolicy.kt','freecfg/FreeSourceRegistry.kt','ProtocolForms.kt','OblivionOptions.kt','PsiphonConfig.kt','ConfigShare.kt','engine/SingBoxConfig.kt','GhajarPaymentPolicy.kt','BrandConfig.kt','ProxyConfig.kt','ConfigParser.kt','ForeignImport.kt','GhajarCompatibilityImport.kt','plugins/PluginModel.kt','plugins/PluginProfiles.kt','plugins/PluginTrust.kt']]
sources += [b/'configtoolkit'/p for p in ['ConfigToolkitModels.kt','ProfileValidator.kt','FormatDetector.kt','Decoders.kt','NpvContainer.kt','V2RayLinkGenerator.kt','ImportRouter.kt']]
sources += [b/'sharing/DirectShare.kt', b/'sharing/AuthenticatedRelay.kt', b/'sharing/OpenVpnExport.kt']
sources += [b/'engine/EngineRouting.kt', b/'engine/CapabilityRegistry.kt', b/'ConfigBuilder.kt', b/'NoiseSpec.kt', fixtures/'builder-stubs.kt', fixtures/'phone-share-constant.kt']
sources += [b/'ReconnectBackoff.kt']
sources += [fixtures/'phase4-stubs.kt', fixtures/'mixedport-stub.kt']
tests=[root/'app/src/test/java/net/gozar/app/Phase4SettingsTest.kt',root/'app/src/test/java/net/gozar/app/ProtocolFormsTest.kt',root/'app/src/test/java/net/gozar/app/plugins/PluginContractsTest.kt',root/'app/src/test/java/net/gozar/app/configtoolkit/ForeignImportTest.kt']
tests += [root/'app/src/test/java/net/gozar/app/sharing/SharingTest.kt',root/'app/src/test/java/net/gozar/app/Phase6RegressionTest.kt']
classpath=':'.join(str(k/p) for p in ['zxing-core.jar','sdk-check','kotlin-stdlib-2.1.0.jar','annotations-13.0.jar','json-20240303.jar','android-all-15.jar','junit-4.13.2.jar','hamcrest-core-1.3.jar'])
cmd=['java','-cp',str(k/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',classpath,'-d',str(k/'phase3-tests.jar'),*map(str,sources+tests)]
r=subprocess.run(cmd,capture_output=True,text=True);print(r.stdout,r.stderr)
if r.returncode==0:
 r=subprocess.run(['java','-cp',str(k/'phase3-tests.jar')+':'+classpath,'org.junit.runner.JUnitCore','net.gozar.app.Phase6RegressionTest','net.gozar.app.sharing.SharingTest','net.gozar.app.Phase4SettingsTest','net.gozar.app.ProtocolFormsTest','net.gozar.app.plugins.PluginContractsTest','net.gozar.app.configtoolkit.ForeignImportTest'],capture_output=True,text=True);print(r.stdout,r.stderr)
if r.returncode: raise SystemExit(r.returncode)
classpath += ':'+str(k/'kotlinx-coroutines-core-jvm-1.6.4.jar')
sources=[b/'GhajarLog.kt',fixtures/'phase6-log-stubs.kt',fixtures/'phase6-core-stubs.kt',fixtures/'phase6-browser-stubs.kt',root/'browser/src/main/java/com/ghajarvpn/browser/SitePermissions.kt',root/'browser/src/test/java/com/ghajarvpn/browser/SitePermissionsStoreTest.kt',root/'app/src/test/java/net/gozar/app/GhajarLogRedactionTest.kt']
subprocess.run(['java','-cp',str(k/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',classpath,'-d',str(k/'phase6-support.jar'),*map(str,sources)],check=True)
subprocess.run(['java','-cp',str(k/'phase6-support.jar')+':'+classpath,'org.junit.runner.JUnitCore','net.gozar.app.GhajarLogRedactionTest','com.ghajarvpn.browser.SitePermissionsStoreTest'],check=True)
sources=[b/'VpnShareAddress.kt',b/'sharing/PhoneSharing.kt',b/'sharing/AuthenticatedRelay.kt',fixtures/'sharing-host-stubs.kt']
subprocess.run(['java','-cp',str(k/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',classpath,'-d',str(k/'sharing-host.jar'),*map(str,sources)],check=True)
subprocess.run(['java','-cp',str(k/'*'),'org.jetbrains.kotlin.cli.jvm.K2JVMCompiler','-no-stdlib','-no-reflect','-classpath',str(k/'phase3-tests.jar')+':'+classpath,'-d',str(k/'corpus.jar'),str(Path(__file__).parent/'XrayCorpus.kt')],check=True)
