package appmire.be.flutterjailbreakdetection

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import com.scottyab.rootbeer.RootBeer

import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.FlutterPlugin.FlutterPluginBinding

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors



class FlutterJailbreakDetectionPlugin : FlutterPlugin, MethodCallHandler {
    private lateinit var context: Context
    private lateinit var channel: MethodChannel

    // RootBeer's isRooted spawns processes and walks the filesystem. Run on the
    // platform thread (where onMethodCall is delivered) it blocks the UI, and
    // every other plugin's channel, for as long as it takes at app startup.
    // The checks run here instead and post their answer back to the main
    // thread, because MethodChannel.Result must be called on the platform thread.
    private lateinit var executor: ExecutorService
    private val mainHandler = Handler(Looper.getMainLooper())


    override fun onAttachedToEngine(binding: FlutterPluginBinding) {
        channel = MethodChannel(binding.binaryMessenger, "flutter_jailbreak_detection")
        context = binding.applicationContext
        executor = Executors.newSingleThreadExecutor()
        channel.setMethodCallHandler(this)
    }


    override fun onDetachedFromEngine(binding: FlutterPluginBinding) {
        channel.setMethodCallHandler(null)
        // A check already running finishes; its reply goes to a detached
        // engine, which drops it.
        executor.shutdown()
    }


    private fun isDevMode(): Boolean {
        return Settings.Secure.getInt(
            context.contentResolver,
            Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0
        ) != 0
    }


    private fun runInBackground(result: Result, check: () -> Boolean) {
        executor.execute {
            val outcome = runCatching(check)
            mainHandler.post {
                outcome.fold(
                    onSuccess = { result.success(it) },
                    // What the engine logs and replies when onMethodCall itself
                    // throws, so a failed check reaches Dart as it did before.
                    onFailure = {
                        Log.e("FlutterJailbreakDetection", "Failed to handle method call", it)
                        result.error("error", it.message, null)
                    }
                )
            }
        }
    }


    override fun onMethodCall(call: MethodCall, result: Result): Unit {
        if (call.method.equals("jailbroken")) {
            runInBackground(result) { RootBeer(context).isRooted }
        } else if (call.method.equals("developerMode")) {
            runInBackground(result) { isDevMode() }
        } else {
            result.notImplemented()
        }
    }


}
