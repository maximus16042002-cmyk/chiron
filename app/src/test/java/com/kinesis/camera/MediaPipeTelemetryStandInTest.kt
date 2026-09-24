package com.kinesis.camera

import com.google.android.datatransport.Encoding
import com.google.android.datatransport.Event
import com.google.android.datatransport.cct.CCTDestination
import com.google.android.datatransport.runtime.TransportRuntime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.File
import java.lang.invoke.MethodType
import java.util.zip.ZipFile

/**
 * Permanent guard - DECISIONS.md P3-18, the first Phase B finding.
 *
 * On the Nothing Phone 2a, opening the live screen crashed with
 * `NoClassDefFoundError: com.google.android.datatransport.runtime.TransportRuntime`.
 * Creating any MediaPipe task builds a usage-stats logger, `RemoteLoggingClient`,
 * that reports to Google through the datatransport library P3-13 excluded so the
 * app would send nothing. The app now carries inert stand-ins for exactly the
 * members that logger links against, so it constructs, and whatever it logs is
 * discarded.
 *
 * These tests read MediaPipe's own bytecode rather than a list typed by hand,
 * so a MediaPipe upgrade that needs more is caught here, before a device is.
 * They check what the device's linker checks - each referenced field and method,
 * by name and exact descriptor - not only that the class names exist: a new call
 * on an existing class would pass a class-name check and still crash the phone.
 */
class MediaPipeTelemetryStandInTest {

    private val loader = javaClass.classLoader!!

    /** A field or method MediaPipe's bytecode names, as its constant pool records it. */
    private data class MemberRef(val kind: Kind, val owner: String, val name: String, val descriptor: String) {
        enum class Kind { FIELD, METHOD, INTERFACE_METHOD, INVOKE_DYNAMIC }

        override fun toString() = "$kind $owner.$name$descriptor"
    }

    private class ScannedClass(val name: String, val bytes: ByteArray)

    /**
     * Every class in every MediaPipe jar the app compiles against - tasks-core,
     * which holds the logger, and tasks-vision, which holds the pose landmarker.
     */
    private fun mediaPipeClasses(): List<ScannedClass> {
        val anchors = listOf(
            "com.google.mediapipe.tasks.core.logging.RemoteLoggingClient",
            "com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker",
        )
        val locations = anchors.map { anchor ->
            val url = Class.forName(anchor, false, loader).protectionDomain?.codeSource?.location
                ?: error("cannot locate the jar $anchor was loaded from")
            File(url.toURI())
        }.toSet()
        val classes = ArrayList<ScannedClass>()
        for (location in locations) {
            if (location.isFile) {
                ZipFile(location).use { jar ->
                    for (entry in jar.entries()) {
                        if (entry.name.endsWith(".class")) {
                            classes += ScannedClass(entry.name, jar.getInputStream(entry).readBytes())
                        }
                    }
                }
            } else {
                location.walkTopDown().filter { it.isFile && it.extension == "class" }.forEach {
                    classes += ScannedClass(it.relativeTo(location).path.replace('\\', '/'), it.readBytes())
                }
            }
        }
        return classes
    }

    /** Every datatransport type named anywhere in MediaPipe's classes, and who names it. */
    private fun typesByReferrer(): Map<String, Set<String>> {
        val pattern = Regex("com/google/android/datatransport/[A-Za-z0-9_/$]+")
        val found = HashMap<String, MutableSet<String>>()
        for (scanned in mediaPipeClasses()) {
            val text = String(scanned.bytes, Charsets.ISO_8859_1)
            for (match in pattern.findAll(text)) found.getOrPut(scanned.name) { HashSet() } += match.value
        }
        return found
    }

    /**
     * The member references in one class file's constant pool that point into
     * datatransport. Written against the class-file format (JVMS 4.4) because the
     * constant pool is exactly what the linker resolves on the device.
     */
    private fun datatransportMembers(bytes: ByteArray): List<MemberRef> {
        val input = DataInputStream(ByteArrayInputStream(bytes))
        check(input.readInt() == 0xCAFEBABE.toInt()) { "not a class file" }
        input.readUnsignedShort() // minor
        input.readUnsignedShort() // major
        val count = input.readUnsignedShort()
        val utf8 = arrayOfNulls<String>(count)
        val classNameIndex = IntArray(count)
        val nameAndType = arrayOfNulls<IntArray>(count)
        val refs = ArrayList<IntArray>() // tag, class-or-bootstrap index, name-and-type index
        var index = 1
        while (index < count) {
            when (val tag = input.readUnsignedByte()) {
                1 -> utf8[index] = input.readUTF()
                3, 4 -> input.readInt()
                5, 6 -> { input.readLong(); index++ }
                7 -> classNameIndex[index] = input.readUnsignedShort()
                8, 16, 19, 20 -> input.readUnsignedShort()
                9, 10, 11, 17, 18 -> refs += intArrayOf(tag, input.readUnsignedShort(), input.readUnsignedShort())
                12 -> nameAndType[index] = intArrayOf(input.readUnsignedShort(), input.readUnsignedShort())
                15 -> { input.readUnsignedByte(); input.readUnsignedShort() }
                else -> error("unknown constant-pool tag $tag at $index")
            }
            index++
        }
        val members = ArrayList<MemberRef>()
        for ((tag, owner, nat) in refs) {
            val (nameIndex, descriptorIndex) = nameAndType[nat]!!
            val name = utf8[nameIndex]!!
            val descriptor = utf8[descriptorIndex]!!
            when (tag) {
                9, 10, 11 -> {
                    val ownerName = utf8[classNameIndex[owner]]!!
                    if (!ownerName.startsWith(DATATRANSPORT)) continue
                    val kind = when (tag) {
                        9 -> MemberRef.Kind.FIELD
                        10 -> MemberRef.Kind.METHOD
                        else -> MemberRef.Kind.INTERFACE_METHOD
                    }
                    members += MemberRef(kind, ownerName, name, descriptor)
                }
                // A lambda converted to a datatransport interface: the interface
                // must declare the method the lambda implements.
                18 -> {
                    val produced = descriptor.substringAfterLast(')')
                    if (!produced.startsWith("L$DATATRANSPORT")) continue
                    members += MemberRef(MemberRef.Kind.INVOKE_DYNAMIC, produced.drop(1).dropLast(1), name, descriptor)
                }
            }
        }
        return members
    }

    private fun referencedMembers(): Set<MemberRef> =
        mediaPipeClasses().flatMap { datatransportMembers(it.bytes) }.toSet()

    private fun type(internalName: String): Class<*> = Class.forName(internalName.replace('/', '.'), false, loader)

    /**
     * The P3-18 crash, reproduced on the JVM: remove a stand-in and this fails
     * with the class MediaPipe could not find, exactly as the phone did.
     */
    @Test
    fun every_datatransport_member_mediapipe_links_against_resolves_in_this_app() {
        val referenced = referencedMembers()
        assertTrue("found no datatransport references, so this proves nothing", referenced.isNotEmpty())
        val problems = ArrayList<String>()
        for (ref in referenced) {
            val owner = try {
                type(ref.owner)
            } catch (missing: ClassNotFoundException) {
                problems += "$ref: ${ref.owner} is not in the app - the P3-18 crash"
                continue
            }
            when (ref.kind) {
                MemberRef.Kind.FIELD -> {
                    val field = runCatching { owner.getField(ref.name) }.getOrNull()
                    val expected = MethodType.fromMethodDescriptorString("()" + ref.descriptor, loader).returnType()
                    if (field == null || field.type != expected) problems += "$ref: no such public field"
                }
                MemberRef.Kind.METHOD, MemberRef.Kind.INTERFACE_METHOD -> {
                    // A class-versus-interface mismatch is IncompatibleClassChangeError on the device.
                    if (owner.isInterface != (ref.kind == MemberRef.Kind.INTERFACE_METHOD)) {
                        problems += "$ref: ${ref.owner} is ${if (owner.isInterface) "an interface" else "a class"}"
                        continue
                    }
                    val signature = MethodType.fromMethodDescriptorString(ref.descriptor, loader)
                    val method = runCatching { owner.getMethod(ref.name, *signature.parameterArray()) }.getOrNull()
                    if (method == null || method.returnType != signature.returnType()) {
                        problems += "$ref: no such public method"
                    }
                }
                MemberRef.Kind.INVOKE_DYNAMIC -> {
                    if (!owner.isInterface || owner.methods.none { it.name == ref.name }) {
                        problems += "$ref: ${ref.owner} is not an interface declaring ${ref.name}"
                    }
                }
            }
        }
        assertTrue("MediaPipe links against members this app does not provide:\n" + problems.joinToString("\n"), problems.isEmpty())
    }

    /**
     * Pins the surface to what MediaPipe 0.10.21 was read to need. An upgrade
     * that needs more fails here and is reviewed - does the new call send
     * anything? - rather than absorbed by writing another stand-in.
     */
    @Test
    fun mediapipe_needs_exactly_the_members_the_stand_ins_were_written_for() {
        val expected = setOf(
            "METHOD com/google/android/datatransport/runtime/TransportRuntime.initialize(Landroid/content/Context;)V",
            "METHOD com/google/android/datatransport/runtime/TransportRuntime.getInstance()" +
                "Lcom/google/android/datatransport/runtime/TransportRuntime;",
            "METHOD com/google/android/datatransport/runtime/TransportRuntime.newFactory(" +
                "Lcom/google/android/datatransport/runtime/Destination;)Lcom/google/android/datatransport/TransportFactory;",
            "FIELD com/google/android/datatransport/cct/CCTDestination.INSTANCE" +
                "Lcom/google/android/datatransport/cct/CCTDestination;",
            "METHOD com/google/android/datatransport/Encoding.of(Ljava/lang/String;)Lcom/google/android/datatransport/Encoding;",
            "INVOKE_DYNAMIC com/google/android/datatransport/Transformer.apply()Lcom/google/android/datatransport/Transformer;",
            "INTERFACE_METHOD com/google/android/datatransport/TransportFactory.getTransport(" +
                "Ljava/lang/String;Ljava/lang/Class;Lcom/google/android/datatransport/Encoding;" +
                "Lcom/google/android/datatransport/Transformer;)Lcom/google/android/datatransport/Transport;",
            "METHOD com/google/android/datatransport/Event.ofData(Ljava/lang/Object;)Lcom/google/android/datatransport/Event;",
            "INTERFACE_METHOD com/google/android/datatransport/Transport.send(Lcom/google/android/datatransport/Event;)V",
        )
        assertEquals(expected, referencedMembers().map { it.toString() }.toSet())
    }

    /** If a MediaPipe upgrade starts logging from somewhere new, that is reviewed, not absorbed. */
    @Test
    fun only_the_remote_logging_client_touches_datatransport() {
        assertEquals(
            setOf("com/google/mediapipe/tasks/core/logging/RemoteLoggingClient.class"),
            typesByReferrer().keys,
        )
    }

    /** The stand-ins discard: nothing is kept, scheduled or sent. */
    @Test
    fun a_logged_event_goes_nowhere() {
        TransportRuntime.initialize(null)
        val transport = TransportRuntime.getInstance()
            .newFactory(CCTDestination.INSTANCE)
            .getTransport("COREML_ON_DEVICE_SOLUTIONS", String::class.java, Encoding.of("proto")) { it.toByteArray() }
        val first = Event.ofData("usage stats")
        val second = Event.ofData("more usage stats")
        transport.send(first)
        // One shared event for every payload: no event holds what was logged.
        assertTrue("an event kept hold of its payload", first === second)
    }

    /**
     * Google's real telemetry stack must not be in the app: its Clearcut
     * uploader, its on-disk event store, its upload scheduler.
     */
    @Test
    fun googles_telemetry_uploader_and_event_store_are_absent() {
        for (name in listOf(
            "com.google.android.datatransport.cct.CctTransportBackend",
            "com.google.android.datatransport.runtime.scheduling.persistence.SQLiteEventStore",
            "com.google.android.datatransport.runtime.scheduling.jobscheduling.JobInfoSchedulerService",
            "com.google.android.datatransport.runtime.backends.TransportBackend",
        )) {
            try {
                Class.forName(name, false, loader)
                fail("$name is on the app's classpath - Google's telemetry stack has come back")
            } catch (expected: ClassNotFoundException) {
                // Absent, as it must be.
            }
        }
    }

    private companion object {
        const val DATATRANSPORT = "com/google/android/datatransport/"
    }
}
