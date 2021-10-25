
package xyz.room409.serif.serif_shared

import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.lang.invoke.MethodHandle
import java.security.SecureRandom

import xyz.room409.WasmOlm

data class DecryptedMessage(val plaintext: String, val message_index: Int)
class Olm() {
    val random = SecureRandom()
    val memory = ByteBuffer.allocate(65536*4)
    //(import "env" "__assert_fail" (func $__assert_fail (type 11)))
    //(import "env" "emscripten_resize_heap" (func $emscripten_resize_heap (type 0)))
    //(import "env" "emscripten_memcpy_big" (func $emscripten_memcpy_big (type 1)))
    //(import "env" "memory" (memory (;0;) 4 4))
    //(import "env" "table" (table (;0;) 9 funcref))
    val olm = WasmOlm(memory, null, null, null, Array(9) { null as? MethodHandle })

    val OLM_ERROR = olm.olm_error()

    fun version(): Triple<Byte,Byte,Byte> {
        val major_ptr = olm.malloc(1)
        val minor_ptr = olm.malloc(1)
        val patch_ptr = olm.malloc(1)
        olm.olm_get_library_version(major_ptr, minor_ptr, patch_ptr)
        val major = memory.get(major_ptr)
        val minor = memory.get(minor_ptr)
        val patch = memory.get(patch_ptr)
        olm.free(major_ptr)
        olm.free(minor_ptr)
        olm.free(patch_ptr)
        return Triple(major, minor, patch)
    }
    fun get_string(ptr: Int, size: Int): String {
        return String(memory.array(), ptr, size)
    }
    fun get_string(ptr: Int): String {
        var end = ptr
        while (memory.get(end) != 0.toByte()) {
            end += 1
        }
        return get_string(ptr, end-ptr-1)
    }

    fun stack(size: Int, f: (Int) -> Unit) {
        val sp = olm.stackSave()
        val s = olm.stackAlloc(size)
        f(s)
        for (i in 0..(size-1)) {
            memory.put(s + i, 0.toByte())
        }
        olm.stackRestore(sp)
    }
    fun stack(a: ByteArray, f: (Int, Int) -> Unit) {
        val sp = olm.stackSave()
        val s_ptr = olm.stackAlloc(a.size)
        copy_array_to_buffer(a, s_ptr)
        f(s_ptr, a.size)
        for (i in 0..(a.size-1)) {
            memory.put(s_ptr + i, 0.toByte())
        }
        olm.stackRestore(sp)
    }
    fun stack(s: String, f: (Int, Int) -> Unit) {
        stack(s.toByteArray(), f)
    }
    fun random_stack(size: Int, f: (Int) -> Unit) {
        val a = ByteArray(size)
        random.nextBytes(a)
        stack(a) { buf, _size ->
            f(buf)
        }
    }

    fun malloc_buffer(size: Int, f: (Int) -> Unit) {
        val b = olm.malloc(size)
        f(b)
        for (i in 0..(size-1)) {
            memory.put(b + i, 0.toByte())
        }
        olm.free(b)
    }
    fun malloc_buffer(a: ByteArray, f: (Int, Int) -> Unit) {
        val b = olm.malloc(a.size)
        copy_array_to_buffer(a, b)
        f(b, a.size)
        for (i in 0..(a.size-1)) {
            memory.put(b + i, 0.toByte())
        }
        olm.free(b)
    }
    fun malloc_buffer(s: String, f: (Int, Int) -> Unit) {
        malloc_buffer(s.toByteArray(), f)
    }
    fun copy_array_to_buffer(a: ByteArray, b: Int) {
        for (i in 0..(a.size-1)) {
            memory.put(b + i, a[i])
        }
    }

    inner class OutboundGroupSession() {
        val size = olm.olm_outbound_group_session_size()
        val buf = olm.malloc(size)
        val ptr = olm.olm_outbound_group_session(buf)

        fun free() {
            olm.olm_clear_outbound_group_session(ptr)
            olm.free(ptr)
        }

        fun error_check(x: Int): Int {
            if (x == OLM_ERROR) {
                val error_str = get_string(olm.olm_outbound_group_session_last_error(ptr))
                throw Exception(error_str)
            }
            return x
        }
        fun pickle(key: String): String {
            var result: String? = null
            val pickle_length = error_check(olm.olm_pickle_outbound_group_session_length(ptr))
            stack(key) { key_buffer, key_buffer_size ->
                stack(pickle_length + 1) { pickle_buffer ->
                    error_check(
                        olm.olm_pickle_outbound_group_session(
                            ptr,
                            key_buffer, key_buffer_size,
                            pickle_buffer, pickle_length
                        )
                    )
                    result = get_string(pickle_buffer, pickle_length)
                }
            }
            return result!!
        }
        fun unpickle(key: String, pickle: String) {
            stack(key) { key_buffer, key_buffer_size ->
                stack(pickle) { pickle_buffer, pickle_buffer_size ->
                    error_check(
                        olm.olm_unpickle_outbound_group_session(
                            ptr,
                            key_buffer, key_buffer_size,
                            pickle_buffer, pickle_buffer_size
                        )
                    )
                }
            }
        }
        fun create() {
            val random_length = error_check(olm.olm_init_outbound_group_session_random_length(ptr))
            random_stack(random_length) { random ->
                error_check(olm.olm_init_outbound_group_session(
                    ptr, random, random_length
                ))
            }
        }
        fun encrypt(plaintext: String): String {
            var result: String? = null
            stack(plaintext) { plaintext_buffer, plaintext_size ->
                val message_length = error_check(
                    olm.olm_group_encrypt_message_length(ptr, plaintext_size)
                )
                val message_buffer = olm.malloc(message_length + 1)
                error_check(
                    olm.olm_group_encrypt(
                        ptr,
                        plaintext_buffer, plaintext_size,
                        message_buffer, message_length
                    )
                )
                result = get_string(message_buffer, message_length)
                olm.free(message_buffer)
            }
            return result!!
        }
        fun session_id(): String {
            var result: String? = null
            val length = error_check(
                olm.olm_outbound_group_session_id_length(ptr)
            )
            stack(length + 1) { session_id ->
                error_check(
                    olm.olm_outbound_group_session_id(ptr, session_id, length)
                )
                result = get_string(session_id, length)
            }
            return result!!
        }
        fun session_key(): String {
            var result: String? = null
            val key_length = error_check(
                olm.olm_outbound_group_session_key_length(ptr)
            )
            stack(key_length + 1) { key ->
                error_check(
                    olm.olm_outbound_group_session_key(ptr, key, key_length)
                )
                result = get_string(key, key_length)
            }
            return result!!
        }
        fun message_index(): Int {
            return error_check(
                olm.olm_outbound_group_session_message_index(ptr)
            )
        }
    }

    inner class InboundGroupSession() {
        val size = olm.olm_inbound_group_session_size()
        val buf = olm.malloc(size)
        val ptr = olm.olm_inbound_group_session(buf)

        fun free() {
            olm.olm_clear_inbound_group_session(ptr)
            olm.free(ptr)
        }

        fun error_check(x: Int): Int {
            if (x == OLM_ERROR) {
                val error_str = get_string(olm.olm_inbound_group_session_last_error(ptr))
                throw Exception(error_str)
            }
            return x
        }
        fun pickle(key: String): String {
            var result: String? = null
            val pickle_length = error_check(olm.olm_pickle_inbound_group_session_length(ptr))
            stack(key) { key_buffer, key_buffer_size ->
                stack(pickle_length + 1) { pickle_buffer ->
                    error_check(
                        olm.olm_pickle_inbound_group_session(
                            ptr,
                            key_buffer, key_buffer_size,
                            pickle_buffer, pickle_length
                        )
                    )
                    result = get_string(pickle_buffer, pickle_length)
                }
            }
            return result!!
        }
        fun unpickle(key: String, pickle: String) {
            stack(key) { key_buffer, key_buffer_size ->
                stack(pickle) { pickle_buffer, pickle_buffer_size ->
                    error_check(
                        olm.olm_unpickle_inbound_group_session(
                            ptr,
                            key_buffer, key_buffer_size,
                            pickle_buffer, pickle_buffer_size
                        )
                    )
                }
            }
        }
        fun create(session_key: String) {
            stack(session_key) { key_buffer, key_buffer_size ->
                error_check(olm.olm_init_inbound_group_session(
                    ptr, key_buffer, key_buffer_size
                ))
            }
        }
        fun import_session(session_key: String) {
            stack(session_key) { key_buffer, key_buffer_size ->
                error_check(olm.olm_import_inbound_group_session(
                    ptr, key_buffer, key_buffer_size
                ))
            }
        }
        fun decrypt(message: String): DecryptedMessage {
            var result: DecryptedMessage? = null
            malloc_buffer(message) { message_buffer, message_buffer_length ->
                val max_plaintext_length = error_check(olm.olm_group_decrypt_max_plaintext_length(
                    ptr, message_buffer, message_buffer_length + 1
                ))
                // finding the length destroys the buffer, must re-copy
                copy_array_to_buffer(message.toByteArray(), message_buffer)
                malloc_buffer(max_plaintext_length + 1) { plaintext_buffer ->
                    stack(4) { message_index ->
                        val plaintext_length = error_check(olm.olm_group_decrypt(
                            ptr, message_buffer, message_buffer_length,
                            plaintext_buffer, max_plaintext_length, message_index
                        ))
                        result = DecryptedMessage(
                            plaintext = get_string(plaintext_buffer, plaintext_length),
                            message_index = memory.getInt(message_index)
                        )
                    }
                }
            }
            return result!!
        }
        fun session_id(): String {
            var result: String? = null
            val length = error_check(olm.olm_inbound_group_session_id_length(
                ptr
            ))
            stack(length + 1) { session_id ->
                error_check(olm.olm_inbound_group_session_id(
                    ptr, session_id, length
                ))
                result = get_string(session_id, length)
            }
            return result!!
        }
        fun first_known_index(): Int {
            return error_check(olm.olm_inbound_group_session_first_known_index(
                ptr
            ))
        }
        fun export_session(message_index: Int): String {
            var result: String? = null
            val key_length = error_check(olm.olm_export_inbound_group_session_length(
                ptr
            ))
            stack(key_length + 1) { key ->
                error_check(olm.olm_export_inbound_group_session(
                    ptr, key, key_length, message_index
                ))
                result = get_string(key, key_length)
            }
            return result!!
        }
    }

    inner class Account() {
        val size = olm.olm_account_size()
        val buf = olm.malloc(size)
        val ptr = olm.olm_account(buf)
    }
}

fun olm_test() {
    println("Testing WasmOlm")
    val olm = Olm()
    println("\tOlm ${olm.version()}")
}


