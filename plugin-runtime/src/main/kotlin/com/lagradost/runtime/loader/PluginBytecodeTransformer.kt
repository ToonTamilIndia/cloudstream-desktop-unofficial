package com.lagradost.runtime.loader

import org.objectweb.asm.*
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object PluginBytecodeTransformer {

    fun transform(jarFile: File) {
        val tempFile = File(jarFile.absolutePath + ".tmp")
        ZipInputStream(FileInputStream(jarFile)).use { zis ->
            ZipOutputStream(FileOutputStream(tempFile)).use { zos ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val newEntry = ZipEntry(entry.name)
                    zos.putNextEntry(newEntry)

                    val bytes = zis.readBytes()
                    if (entry.name.endsWith(".class")) {
                        val reader = ClassReader(bytes)
                        val writer = ClassWriter(0)

                        val visitor = object : ClassVisitor(Opcodes.ASM9, writer) {

                            override fun visitMethod(
                                access: Int,
                                name: String,
                                descriptor: String?,
                                signature: String?,
                                exceptions: Array<out String>?,
                            ): MethodVisitor {
                                val mv = super.visitMethod(access, fixMethodName(name), descriptor, signature, exceptions)
                                return object : MethodVisitor(Opcodes.ASM9, mv) {
                                    override fun visitMethodInsn(
                                        opcode: Int,
                                        owner: String,
                                        methodName: String,
                                        descriptor: String?,
                                        isInterface: Boolean,
                                    ) {
                                        var newOpcode = opcode
                                        var newOwner = owner
                                        var newDesc = descriptor

                                        if (owner == "java/lang/Runtime" && (methodName == "exec" || methodName == "loadLibrary" || methodName == "load" || methodName == "exit" || methodName == "halt")) {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                            newDesc = descriptor?.replace("(", "(Ljava/lang/Runtime;")
                                        } else if (owner == "java/lang/Runtime" && methodName == "availableProcessors" && descriptor == "()I") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                            newDesc = "(Ljava/lang/Runtime;)I"
                                        } else if (owner == "java/lang/Runtime" && (methodName == "maxMemory" || methodName == "totalMemory" || methodName == "freeMemory") && descriptor == "()J") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/RuntimeStub"
                                            newDesc = "(Ljava/lang/Runtime;)J"
                                        } else if (owner == "java/lang/System" && (methodName == "exit" || methodName == "loadLibrary" || methodName == "load" || methodName == "setSecurityManager")) {
                                            newOwner = "com/lagradost/runtime/loader/stubs/SystemStub"
                                        } else if (owner == "java/lang/reflect/Method" && methodName == "invoke" && descriptor == "(Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                            newDesc = "(Ljava/lang/reflect/Method;Ljava/lang/Object;[Ljava/lang/Object;)Ljava/lang/Object;"
                                        } else if (owner == "java/lang/reflect/Field" && methodName == "get" && descriptor == "(Ljava/lang/Object;)Ljava/lang/Object;") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                            newDesc = "(Ljava/lang/reflect/Field;Ljava/lang/Object;)Ljava/lang/Object;"
                                        } else if (owner == "java/lang/reflect/Field" && methodName == "set" && descriptor == "(Ljava/lang/Object;Ljava/lang/Object;)V") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                            newDesc = "(Ljava/lang/reflect/Field;Ljava/lang/Object;Ljava/lang/Object;)V"
                                        } else if (owner == "java/lang/reflect/Constructor" && methodName == "newInstance" && descriptor == "([Ljava/lang/Object;)Ljava/lang/Object;") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                            newDesc = "(Ljava/lang/reflect/Constructor;[Ljava/lang/Object;)Ljava/lang/Object;"
                                        } else if ((owner == "java/lang/reflect/AccessibleObject" || owner == "java/lang/reflect/Method" || owner == "java/lang/reflect/Field" || owner == "java/lang/reflect/Constructor") && methodName == "setAccessible" && descriptor == "(Z)V") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/ReflectionStub"
                                            newDesc = "(Ljava/lang/reflect/AccessibleObject;Z)V"
                                        } else if (owner == "java/net/URL" && methodName == "openConnection") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                            newDesc = descriptor?.replace("(", "(Ljava/net/URL;")
                                        } else if (owner == "java/net/URL" && methodName == "openStream" && descriptor == "()Ljava/io/InputStream;") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                            newDesc = "(Ljava/net/URL;)Ljava/io/InputStream;"
                                        } else if (owner == "java/net/URL" && methodName == "getContent") {
                                            newOpcode = Opcodes.INVOKESTATIC
                                            newOwner = "com/lagradost/runtime/loader/stubs/URLStub"
                                            newDesc = descriptor?.replace("(", "(Ljava/net/URL;")
                                        }

                                        super.visitMethodInsn(newOpcode, newOwner, fixMethodName(methodName), newDesc, isInterface)
                                    }
                                }
                            }
                        }

                        reader.accept(visitor, 0)
                        zos.write(writer.toByteArray())
                    } else {
                        zos.write(bytes)
                    }

                    zos.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }
        jarFile.delete()
        tempFile.renameTo(jarFile)
    }

    private fun fixMethodName(name: String): String {
        return when (name) {
            "constructor_impl" -> "constructor-impl"
            "box_impl" -> "box-impl"
            "unbox_impl" -> "unbox-impl"
            "isSuccess_impl" -> "isSuccess-impl"
            "isFailure_impl" -> "isFailure-impl"
            "getOrNull_impl" -> "getOrNull-impl"
            "exceptionOrNull_impl" -> "exceptionOrNull-impl"
            else -> name
        }
    }
}
