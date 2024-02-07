// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.protocgen;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;
import dev.restate.generated.ext.Ext;
import dev.restate.generated.ext.ServiceType;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class RestateGen extends Generator {

  @Override
  protected List<PluginProtos.CodeGeneratorResponse.Feature> supportedFeatures() {
    return Collections.singletonList(
        PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL);
  }

  @Override
  public List<PluginProtos.CodeGeneratorResponse.File> generateFiles(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    List<DescriptorProtos.FileDescriptorProto> protos =
        request.getProtoFileList().stream()
            .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
            .collect(Collectors.toList());
    ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

    boolean isJavaGen = CodeGenUtils.findParameterOption(request, "java");
    boolean isKotlinGen = CodeGenUtils.findParameterOption(request, "kotlin");
    if (!isJavaGen && !isKotlinGen) {
      // If no parameter specified, default to only java gen
      isJavaGen = true;
    }

    Stream<PluginProtos.CodeGeneratorResponse.File> generatedFiles = Stream.empty();
    if (isJavaGen) {
      generatedFiles = Stream.concat(generatedFiles, generateJava(protos, typeMap));
    }
    if (isKotlinGen) {
      generatedFiles = Stream.concat(generatedFiles, generateKotlin(protos, typeMap));
    }

    return generatedFiles.collect(Collectors.toList());
  }

  private Stream<PluginProtos.CodeGeneratorResponse.File> generateJava(
      List<DescriptorProtos.FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    return findServices(protos, typeMap, false).map(ctx -> buildFile(ctx, "javaStub.mustache"));
  }

  private Stream<PluginProtos.CodeGeneratorResponse.File> generateKotlin(
      List<DescriptorProtos.FileDescriptorProto> protos, ProtoTypeMap typeMap) {
    return findServices(protos, typeMap, true).map(ctx -> buildFile(ctx, "ktStub.mustache"));
  }

  private Stream<ServiceContext> findServices(
      List<DescriptorProtos.FileDescriptorProto> protos,
      ProtoTypeMap typeMap,
      boolean isKotlinGen) {
    return protos.stream()
        .flatMap(
            fileProto ->
                IntStream.range(0, fileProto.getServiceCount())
                    .filter(i -> fileProto.getService(i).getOptions().hasExtension(Ext.serviceType))
                    .mapToObj(
                        serviceIndex -> {
                          ServiceContext serviceContext =
                              buildServiceContext(
                                  typeMap,
                                  isKotlinGen,
                                  fileProto.getSourceCodeInfo().getLocationList(),
                                  fileProto.getService(serviceIndex),
                                  serviceIndex);
                          serviceContext.protoName = fileProto.getName();
                          serviceContext.packageName = extractPackageName(fileProto);
                          return serviceContext;
                        }));
  }

  private String extractPackageName(DescriptorProtos.FileDescriptorProto proto) {
    DescriptorProtos.FileOptions options = proto.getOptions();
    String javaPackage = options.getJavaPackage();
    if (javaPackage != null && !javaPackage.isEmpty()) {
      return javaPackage;
    }

    return proto.getPackage();
  }

  private ServiceContext buildServiceContext(
      ProtoTypeMap typeMap,
      boolean isKotlinGen,
      List<DescriptorProtos.SourceCodeInfo.Location> locations,
      DescriptorProtos.ServiceDescriptorProto serviceProto,
      int serviceIndex) {
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.className = serviceProto.getName() + "Restate";
    if (isKotlinGen) {
      serviceContext.className += "Kt";
    }
    if (isKotlinGen) {
      serviceContext.fileName = serviceContext.className + ".kt";
    } else {
      serviceContext.fileName = serviceContext.className + ".java";
    }
    serviceContext.serviceName = serviceProto.getName();
    serviceContext.deprecated = serviceProto.getOptions().getDeprecated();

    // Resolve context type
    serviceContext.contextType =
        serviceProto.getOptions().getExtension(Ext.serviceType) == ServiceType.UNKEYED
            ? "UnkeyedContext"
            : "KeyedContext";

    // Resolve javadoc
    DescriptorProtos.SourceCodeInfo.Location serviceLocation =
        locations.stream()
            .filter(
                CodeGenUtils.locationPathMatcher(
                    DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER, serviceIndex))
            .findFirst()
            .orElseGet(DescriptorProtos.SourceCodeInfo.Location::getDefaultInstance);
    serviceContext.apidoc =
        CodeGenUtils.getJavadoc(
            CodeGenUtils.getComments(serviceLocation),
            serviceProto.getOptions().getDeprecated(),
            CodeGenUtils.SERVICE_INDENDATION);

    for (int methodIndex = 0; methodIndex < serviceProto.getMethodCount(); methodIndex++) {
      serviceContext.methods.add(
          buildMethodContext(
              typeMap,
              isKotlinGen,
              locations,
              serviceProto.getMethod(methodIndex),
              serviceIndex,
              methodIndex));
    }
    return serviceContext;
  }

  private MethodContext buildMethodContext(
      ProtoTypeMap typeMap,
      boolean isKotlinGen,
      List<DescriptorProtos.SourceCodeInfo.Location> locations,
      DescriptorProtos.MethodDescriptorProto methodProto,
      int serviceIndex,
      int methodIndex) {
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = CodeGenUtils.mixedLower(methodProto.getName(), isKotlinGen);

    // This is needed to avoid clashes with generated oneWay and delayed methods.
    methodContext.topLevelClientMethodName = methodContext.methodName;
    if (methodContext.methodName.equals("oneWay") || methodContext.methodName.equals("delayed")) {
      methodContext.topLevelClientMethodName = "_" + methodContext.topLevelClientMethodName;
    }

    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.isInputEmpty = CodeGenUtils.isGoogleProtobufEmpty(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.isOutputEmpty = CodeGenUtils.isGoogleProtobufEmpty(methodProto.getOutputType());
    methodContext.deprecated = methodProto.getOptions().getDeprecated();

    // Resolve javadoc
    DescriptorProtos.SourceCodeInfo.Location serviceLocation =
        locations.stream()
            .filter(
                CodeGenUtils.locationPathMatcher(
                    DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER,
                    serviceIndex,
                    DescriptorProtos.ServiceDescriptorProto.METHOD_FIELD_NUMBER,
                    methodIndex))
            .findFirst()
            .orElseGet(DescriptorProtos.SourceCodeInfo.Location::getDefaultInstance);
    methodContext.apidoc =
        CodeGenUtils.getJavadoc(
            CodeGenUtils.getComments(serviceLocation),
            methodProto.getOptions().getDeprecated(),
            CodeGenUtils.METHOD_INDENDATION);

    return methodContext;
  }

  private PluginProtos.CodeGeneratorResponse.File buildFile(
      ServiceContext context, String templateFile) {
    String content = applyTemplate(templateFile, context);
    return PluginProtos.CodeGeneratorResponse.File.newBuilder()
        .setName(absoluteFileName(context))
        .setContent(content)
        .build();
  }

  private String absoluteFileName(ServiceContext ctx) {
    String dir = ctx.packageName.replace('.', '/');
    if (dir.isEmpty()) {
      return ctx.fileName;
    } else {
      return dir + "/" + ctx.fileName;
    }
  }

  /** Template class for proto Service objects. */
  private static class ServiceContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 8 LINES
    public String fileName;
    public String protoName;
    public String packageName;
    public String className;
    public String serviceName;
    public String contextType;
    public String apidoc;
    public boolean deprecated;
    public final List<MethodContext> methods = new ArrayList<>();
  }

  /** Template class for proto RPC objects. */
  private static class MethodContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 10 LINES
    public String topLevelClientMethodName;
    public String methodName;
    public String inputType;
    public boolean isInputEmpty;
    public String outputType;

    public boolean isOutputEmpty;
    public String apidoc;
    public boolean deprecated;

    // This method mimics the upper-casing method ogf gRPC to ensure compatibility
    // See
    // https://github.com/grpc/grpc-java/blob/v1.8.0/compiler/src/java_plugin/cpp/java_generator.cpp#L58
    public String methodNameUpperUnderscore() {
      StringBuilder s = new StringBuilder();
      for (int i = 0; i < methodName.length(); i++) {
        char c = methodName.charAt(i);
        s.append(Character.toUpperCase(c));
        if ((i < methodName.length() - 1)
            && Character.isLowerCase(c)
            && Character.isUpperCase(methodName.charAt(i + 1))) {
          s.append('_');
        }
      }
      return s.toString();
    }

    public String methodDescriptorGetter() {
      return CodeGenUtils.mixedLower("get_" + methodName + "_method", false);
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(List.of(new RestateGen()), List.of(Ext.serviceType));
    } else {
      ProtocPlugin.debug(List.of(new RestateGen()), List.of(Ext.serviceType), args[0]);
    }
  }
}
