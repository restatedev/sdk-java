// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import com.google.common.html.HtmlEscapers;
import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import java.util.*;
import java.util.function.Predicate;
import java.util.regex.Pattern;

public final class CodeGenUtils {
  private CodeGenUtils() {}

  // java keywords from: https://docs.oracle.com/javase/specs/jls/se8/html/jls-3.html#jls-3.9
  static final List<CharSequence> JAVA_KEYWORDS =
      Arrays.asList(
          "abstract",
          "assert",
          "boolean",
          "break",
          "byte",
          "case",
          "catch",
          "char",
          "class",
          "const",
          "continue",
          "default",
          "do",
          "double",
          "else",
          "enum",
          "extends",
          "final",
          "finally",
          "float",
          "for",
          "goto",
          "if",
          "implements",
          "import",
          "instanceof",
          "int",
          "interface",
          "long",
          "native",
          "new",
          "package",
          "private",
          "protected",
          "public",
          "return",
          "short",
          "static",
          "strictfp",
          "super",
          "switch",
          "synchronized",
          "this",
          "throw",
          "throws",
          "transient",
          "try",
          "void",
          "volatile",
          "while",
          // additional ones added by us
          "true",
          "false");

  // From https://kotlinlang.org/docs/reference/keyword-reference.html
  static final List<CharSequence> KOTLIN_KEYWORDS =
      Arrays.asList(
          // Hard keywords
          "as",
          "break",
          "class",
          "continue",
          "do",
          "else",
          "false",
          "for",
          "fun",
          "if",
          "in",
          "interface",
          "is",
          "null",
          "object",
          "package",
          "return",
          "super",
          "this",
          "throw",
          "true",
          "try",
          "typealias",
          "typeof",
          "val",
          "var",
          "when",
          "while",

          // Soft keywords
          "by",
          "catch",
          "constructor",
          "delegate",
          "dynamic",
          "field",
          "file",
          "finally",
          "get",
          "import",
          "init",
          "param",
          "property",
          "receiver",
          "set",
          "setparam",
          "where",

          // Modifier keywords
          "actual",
          "abstract",
          "annotation",
          "companion",
          "const",
          "crossinline",
          "data",
          "enum",
          "expect",
          "external",
          "final",
          "infix",
          "inline",
          "inner",
          "internal",
          "lateinit",
          "noinline",
          "open",
          "operator",
          "out",
          "override",
          "private",
          "protected",
          "public",
          "reified",
          "sealed",
          "suspend",
          "tailrec",
          "value",
          "vararg",

          // These aren't keywords anymore but still break some code if unescaped.
          // https://youtrack.jetbrains.com/issue/KT-52315
          "header",
          "impl",

          // Other reserved keywords
          "yield");

  static final String SERVICE_INDENDATION = "    ";
  static final String METHOD_INDENDATION = "        ";

  /**
   * Adjust a method name prefix identifier to follow the JavaBean spec: - decapitalize the first
   * letter - remove embedded underscores & capitalize the following letter
   *
   * <p>Finally, if the result is a reserved java keyword, append an underscore.
   *
   * @param word method name
   * @return lower name
   */
  static String mixedLower(String word, boolean isKotlinGen) {
    StringBuffer w = new StringBuffer();
    w.append(Character.toLowerCase(word.charAt(0)));

    boolean afterUnderscore = false;

    for (int i = 1; i < word.length(); ++i) {
      char c = word.charAt(i);

      if (c == '_') {
        afterUnderscore = true;
      } else {
        if (afterUnderscore) {
          w.append(Character.toUpperCase(c));
        } else {
          w.append(c);
        }
        afterUnderscore = false;
      }
    }

    if (isKotlinGen) {
      if (KOTLIN_KEYWORDS.contains(w)) {
        w.append('_');
      }
    } else {
      if (JAVA_KEYWORDS.contains(w)) {
        w.append('_');
      }
    }

    return w.toString();
  }

  static String firstUppercase(String word) {
    if (word.length() == 1) {
      return String.valueOf(Character.toUpperCase(word.charAt(0)));
    }
    return Character.toUpperCase(word.charAt(0)) + word.substring(1);
  }

  static boolean isGoogleProtobufEmpty(String ty) {
    return ty.equals(".google.protobuf.Empty");
  }

  static Predicate<DescriptorProtos.SourceCodeInfo.Location> locationPathMatcher(int... matcher) {
    return location -> {
      if (location.getPathCount() != matcher.length) {
        return false;
      }
      for (int i = 0; i < matcher.length; i++) {
        if (location.getPath(i) != matcher[i]) {
          return false;
        }
      }
      return true;
    };
  }

  static String getComments(DescriptorProtos.SourceCodeInfo.Location location) {
    return location.getLeadingComments().isEmpty()
        ? location.getTrailingComments()
        : location.getLeadingComments();
  }

  static String getJavadoc(String comments, boolean isDeprecated, String prefix) {
    StringBuilder builder =
        new StringBuilder(prefix).append("/**\n").append(prefix).append(" * <pre>\n");
    Arrays.stream(HtmlEscapers.htmlEscaper().escape(comments).split("\n"))
        .forEach(line -> builder.append(prefix).append(" * ").append(line).append("\n"));
    builder.append(prefix).append(" * </pre>\n");
    if (isDeprecated) {
      builder.append(prefix).append(" * @deprecated\n");
    }
    builder.append(prefix).append(" */");
    return builder.toString();
  }

  // This is in the form of key[=value],key[=value],...
  static boolean findParameterOption(PluginProtos.CodeGeneratorRequest request, String parameter) {
    String[] params = request.getParameter().split(Pattern.quote(","));

    for (String param : params) {
      if (param.contains(parameter)) {
        return true;
      }
    }
    return false;
  }
}
