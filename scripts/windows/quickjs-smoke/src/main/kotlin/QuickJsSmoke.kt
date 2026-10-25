import com.dokar.quickjs.QuickJs
import com.dokar.quickjs.QuickJsException
import com.dokar.quickjs.binding.asyncFunction
import com.dokar.quickjs.binding.function
import com.dokar.quickjs.evaluate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

fun main() = runBlocking {
    println("JVM: ${System.getProperty("java.version")} / ${System.getProperty("os.arch")}")
    withTimeout(30_000) {
        repeat(3) {
            println("Engine $it: create begin")
            QuickJs.create(Dispatchers.Default).use { js ->
                println("Engine $it: created; evaluate begin")
                check(js.evaluate<Long>("21 * 2") == 42L)
                check(js.evaluate<String>("String(42)") == "42")
                check(js.evaluate<Long>("(() => { let captured = 40; return (() => captured + 2)(); })()") == 42L)
                println("Engine $it: evaluation passed")
                check(js.evaluate<String>("String((2n ** 100n) / 3n)") == "422550200076076467165567735125")
                println("Engine $it: BigInt passed")
                val text = "Nuvio \u0000 \uD83D\uDE80 \u6F22\u5B57"
                js.function("nativeText") { text }
                check(js.evaluate<String>("nativeText()") == text)
                check(js.evaluate<String>("'Nuvio \\u0000 \\uD83D\\uDE80 \\u6F22\\u5B57'") == text)
                println("Engine $it: string conversion passed")
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
                println("Engine $it: exceptions and coroutine jobs passed")
                check(js.evaluate<Long>("const a = new Int32Array(new SharedArrayBuffer(4)); Atomics.add(a, 0, 7); Atomics.load(a, 0)") == 7L)
                // QuickJS-KT does not opt into blocking Atomics.wait on its JVM threads.
                check(js.evaluate<Boolean>("try { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 2); false } catch(e) { e instanceof TypeError }") )
                println("Engine $it: Atomics passed; close begin")
            }
            println("Engine $it: closed")
        }
    }
    println("PASS: initialization, evaluation, UTF-16/NUL conversion, exceptions/recovery, callbacks, suspend/promise jobs, Atomics, repeated cleanup")
}
