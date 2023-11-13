package dev.restate.sdk.blocking.gen;

import static dev.restate.sdk.blocking.gen.CodeGenUtils.*;

import com.google.protobuf.DescriptorProtos;
import com.google.protobuf.compiler.PluginProtos;
import com.salesforce.jprotoc.Generator;
import com.salesforce.jprotoc.GeneratorException;
import com.salesforce.jprotoc.ProtoTypeMap;
import com.salesforce.jprotoc.ProtocPlugin;
import dev.restate.generated.ext.Ext;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class JavaBlockingGen extends Generator {

  @Override
  protected List<PluginProtos.CodeGeneratorResponse.Feature> supportedFeatures() {
    return Collections.singletonList(
        PluginProtos.CodeGeneratorResponse.Feature.FEATURE_PROTO3_OPTIONAL);
  }

  @Override
  public List<PluginProtos.CodeGeneratorResponse.File> generateFiles(
      PluginProtos.CodeGeneratorRequest request) throws GeneratorException {
    ProtoTypeMap typeMap = ProtoTypeMap.of(request.getProtoFileList());

    List<DescriptorProtos.FileDescriptorProto> protosToGenerate =
        request.getProtoFileList().stream()
            .filter(protoFile -> request.getFileToGenerateList().contains(protoFile.getName()))
            .collect(Collectors.toList());

    List<ServiceContext> services = findServices(protosToGenerate, typeMap);
    return generateFiles(services);
  }

  private List<ServiceContext> findServices(
      List<DescriptorProtos.FileDescriptorProto> protos, ProtoTypeMap typeMap) {

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
                                  fileProto.getSourceCodeInfo().getLocationList(),
                                  fileProto.getService(serviceIndex),
                                  serviceIndex);
                          serviceContext.protoName = fileProto.getName();
                          serviceContext.packageName = extractPackageName(fileProto);
                          return serviceContext;
                        }))
        .collect(Collectors.toList());
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
      List<DescriptorProtos.SourceCodeInfo.Location> locations,
      DescriptorProtos.ServiceDescriptorProto serviceProto,
      int serviceIndex) {
    ServiceContext serviceContext = new ServiceContext();
    serviceContext.className = serviceProto.getName() + "Restate";
    serviceContext.fileName = serviceContext.className + ".java";
    serviceContext.serviceName = serviceProto.getName();
    serviceContext.deprecated = serviceProto.getOptions().getDeprecated();

    // Resolve javadoc
    DescriptorProtos.SourceCodeInfo.Location serviceLocation =
        locations.stream()
            .filter(
                locationPathMatcher(
                    DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER, serviceIndex))
            .findFirst()
            .orElseGet(DescriptorProtos.SourceCodeInfo.Location::getDefaultInstance);
    serviceContext.javadoc =
        CodeGenUtils.getJavadoc(
            CodeGenUtils.getComments(serviceLocation),
            serviceProto.getOptions().getDeprecated(),
            SERVICE_INDENDATION);

    for (int methodIndex = 0; methodIndex < serviceProto.getMethodCount(); methodIndex++) {
      serviceContext.methods.add(
          buildMethodContext(
              typeMap, locations, serviceProto.getMethod(methodIndex), serviceIndex, methodIndex));
    }
    return serviceContext;
  }

  private MethodContext buildMethodContext(
      ProtoTypeMap typeMap,
      List<DescriptorProtos.SourceCodeInfo.Location> locations,
      DescriptorProtos.MethodDescriptorProto methodProto,
      int serviceIndex,
      int methodIndex) {
    MethodContext methodContext = new MethodContext();
    methodContext.methodName = CodeGenUtils.mixedLower(methodProto.getName());
    // This is needed to avoid clashes with generated oneWay and delayed methods.
    methodContext.topLevelClientMethodName = (methodContext.methodName.equals("oneWay") || methodContext.methodName.equals("delayed")) ? "call" + firstUppercase(methodContext.methodName) : methodContext.methodName;
    methodContext.inputType = typeMap.toJavaTypeName(methodProto.getInputType());
    methodContext.outputType = typeMap.toJavaTypeName(methodProto.getOutputType());
    methodContext.deprecated = methodProto.getOptions().getDeprecated();

    // Resolve javadoc
    DescriptorProtos.SourceCodeInfo.Location serviceLocation =
        locations.stream()
            .filter(
                locationPathMatcher(
                    DescriptorProtos.FileDescriptorProto.SERVICE_FIELD_NUMBER,
                    serviceIndex,
                    DescriptorProtos.ServiceDescriptorProto.METHOD_FIELD_NUMBER,
                    methodIndex))
            .findFirst()
            .orElseGet(DescriptorProtos.SourceCodeInfo.Location::getDefaultInstance);
    methodContext.javadoc =
        CodeGenUtils.getJavadoc(
            CodeGenUtils.getComments(serviceLocation),
            methodProto.getOptions().getDeprecated(),
            METHOD_INDENDATION);

    return methodContext;
  }

  private List<PluginProtos.CodeGeneratorResponse.File> generateFiles(
      List<ServiceContext> services) {
    return services.stream().map(this::buildFile).collect(Collectors.toList());
  }

  private PluginProtos.CodeGeneratorResponse.File buildFile(ServiceContext context) {
    String content = applyTemplate("blockingStub.mustache", context);
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
    public String javadoc;
    public boolean deprecated;
    public final List<MethodContext> methods = new ArrayList<>();
  }

  /** Template class for proto RPC objects. */
  private static class MethodContext {
    // CHECKSTYLE DISABLE VisibilityModifier FOR 10 LINES
  public String topLevelClientMethodName;
    public String methodName;
    public String inputType;
    public String outputType;
    public String javadoc;
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
      return CodeGenUtils.mixedLower("get_" + methodName + "_method");
    }
  }

  public static void main(String[] args) {
    if (args.length == 0) {
      ProtocPlugin.generate(List.of(new JavaBlockingGen()), List.of(Ext.serviceType));
    } else {
      ProtocPlugin.debug(List.of(new JavaBlockingGen()), List.of(Ext.serviceType), args[0]);
    }
  }
}
