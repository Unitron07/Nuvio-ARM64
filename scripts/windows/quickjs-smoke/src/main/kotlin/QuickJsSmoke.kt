import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    println("JVM: ${System.getProperty("java.version")} / ${System.getProperty("os.arch")}")
    withTimeout(30_000) {
        repeat(3) {
            QuickJs.create(Dispatchers.Default).use { js ->
                check(js.evaluate<Long>("21 * 2") == 42L)
                val text = "Nuvio \u0000 \uD83D\uDE80 \u6F22\u5B57"
                js.function("nativeText") { text }
                check(js.evaluate<String>("nativeText()") == text)
                check(js.evaluate<String>("'Nuvio \\u0000 \\uD83D\\uDE80 \\u6F22\\u5B57'") == text)
                try {
                    js.evaluate<Any?>("throw new Error('expected-native-error')")
                    error("JavaScript exception was not propagated")
                } catch (e: QuickJsException) {
                    check(e.message.orEmpty().contains("expected-native-error"))
                }
                check(js.evaluate<Long>("1 + 2") == 3L)
                js.asyncFunction("nativeAsync") { delay(10); "resolved" }
                check(js.evaluate<String>("await nativeAsync()") == "resolved")
                check(js.evaluate<Long>("await Promise.resolve(42)") == 42L)
                check(js.evaluate<Long>("const a = new Int32Array(new SharedArrayBuffer(4)); Atomics.add(a, 0, 7); Atomics.load(a, 0)") == 7L)
                check(js.evaluate<String>("Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 2)") == "timed-out")
            }
        }
    }
    println("PASS: initialization, evaluation, UTF-16/NUL conversion, exceptions/recovery, callbacks, suspend/promise jobs, Atomics, repeated cleanup")
}
