// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors
import com.google.protobuf.Descriptors.FieldDescriptor
import com.google.protobuf.Descriptors.FieldDescriptor.Type.*
import com.google.protobuf.Message
import com.google.protobuf.MessageLite
import dev.restate.sdk.core.TestDefinitions.TestDefinition
import dev.restate.sdk.core.TestDefinitions.TestSuite
import org.junit.jupiter.api.Test
import java.io.File

class NoAICodeGeneration {
    @Test
    fun generate() {
        val out = File("output.rs")

        out.appendText("""
            use super::*;

            use crate::service_protocol::messages::{*, start_message::StateEntry};
            
        """.trimIndent())

        KotlinCoroutinesTests().definitions().forEach {
            writeTestSuite(it, out)
        }
    }

    private fun writeTestSuite(testSuite: TestSuite, out: File) {
        out.appendText(
            """mod ${camelToSnake(testSuite.javaClass.simpleName)} {
                |   use super::*;
                |   
                |   use test_log::test;
                |   
            """.trimMargin())
        testSuite.definitions().forEach { writeTestDefinition(it, out) }
        out.appendText(
            """
                }
                
                """.trimIndent())
    }

    private fun writeTestDefinition(testDefinition: TestDefinition, out: File) {
        val isOnlyUnbuffered = testDefinition.isOnlyUnbuffered
        val testName = camelToSnake(testDefinition.testCaseName).replace("#", "_").replace(":", "_").replace(" ", "_")
        val inputMessages = testDefinition.input.map { messageToRust(it.message()) }
        val outputMessages = if (testDefinition.output == null) {
            listOf("Empty" to """todo!("Unknown output")""")
        } else {
            testDefinition.output.map { it.javaClass.simpleName to messageToRust(it) }
        }

        out.appendText("""
            #[test]
            fn $testName() {
                let mut output = VMTestCase::new(Version::V1)
                    ${inputMessages.joinToString(separator = "") { ".input($it)\n" }}
                    .run(|vm| {
                        // testName = $testName
                        // isOnlyUnbuffered = $isOnlyUnbuffered
                        todo!("Implement sys_")
                    });
                
                ${outputMessages.joinToString(separator = "") { "assert_eq!(output.next_decoded::<${it.first}>().unwrap(), ${it.second});\n" }}
                assert_eq!(output.next(), None);
            }
            
        """.trimIndent())
    }


    private fun messageToRust(message: MessageLite): String {
        val msg = message as Message
        var out = "${msg.javaClass.simpleName} {\n"

        for (field in msg.allFields) {
            out += printField(field.key, field.value) + ",\n"
        }

        out += """
                ..Default::default()
            }
        """.trimIndent()

        return out
    }

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    private fun printField(field: FieldDescriptor, value: Any): String {
        if (field.isRepeated && setOf(INT32, SINT32, SFIXED32, INT64, SINT64, SFIXED64, FLOAT, DOUBLE, UINT32, FIXED32, UINT64, FIXED64).contains(field.type)) {
            return """${field.name}: vec![${(value as List<*>).joinToString(separator = ",") { b -> b.toString() }}]
            """.trimIndent()
        }

        if (field.isRepeated && MESSAGE == field.type) {
            return """${field.name}: vec![${(value as List<MessageLite>).joinToString(separator = ",") { messageToRust(it) }}]
            """.trimIndent()
        }

        if (field.isRepeated && BYTES == field.type) {
            return """${field.name}: vec![${(value as List<ByteString>).joinToString(separator = ",") {
                val byteString = it
                if (byteString.isValidUtf8) {
                    """Bytes::from_static(b"${byteString.toStringUtf8().replace("\"", "\\\"")}")"""
                } else {
                    """Bytes::from_static(&[${
                        byteString.toByteArray().joinToString(separator = ",") { b -> b.toUByte().toString() }
                    }])"""
                }
            }}]
            """.trimIndent()
        }

        val stringifiedValue =
        when (field.getType()) {
            INT32, SINT32, SFIXED32, INT64, SINT64, SFIXED64, FLOAT, DOUBLE, UINT32, FIXED32, UINT64, FIXED64 -> {
                (value as Number).toString()
            }
            BOOL -> {
                if ((value as Boolean)) {
                   "true"
                } else {
                    "false"
                }
            }
            STRING -> """ "$value".to_owned()"""
            BYTES -> {
                val byteString = value as ByteString
                if (byteString.isValidUtf8) {
                    """Bytes::from_static(b"${byteString.toStringUtf8().replace("\"", "\\\"")}")"""
                } else {
                    """Bytes::from_static(&[${
                        byteString.toByteArray().joinToString(separator = ",") {it.toUByte().toString() }
                    }])"""
                }
            }

            ENUM -> {
                val enumValue = value as Descriptors.EnumValueDescriptor
                """${enumValue.type.name}::${enumValue.name}"""
            }
            MESSAGE, GROUP -> messageToRust(value as Message)
            }

        if (field.containingOneof != null) {
            // We need to add the one of
            val oneOfName = field.containingOneof.name
            val rustOneOfEnum = camelToSnake(oneOfName).capitalize()
            val rustVariantName = field.jsonName.capitalize()
            val typeNamespace = camelToSnake(field.containingType.name)
            return """$oneOfName: Some($typeNamespace::$rustOneOfEnum::$rustVariantName($stringifiedValue))"""
        }

        return """${field.name}: $stringifiedValue"""

        }

        private fun camelToSnake(str: String): String {
            // Empty String
            var result = ""

            // Append first character(in lower case)
            // to result string
            val c = str[0]
            result += c.lowercaseChar()

            // Traverse the string from
            // ist index to last index
            for (i in 1 until str.length) {
                val ch = str[i]

                // Check if the character is upper case
                // then append '_' and such character
                // (in lower case) to result string
                if (Character.isUpperCase(ch)) {
                    result += '_'
                    result = (result
                            + ch.lowercaseChar())
                } else {
                    result += ch
                }
            }

            // return the result
            return result
        }
}
