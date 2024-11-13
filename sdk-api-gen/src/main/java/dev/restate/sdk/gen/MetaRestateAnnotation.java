// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.gen;

import dev.restate.sdk.common.ServiceType;
import java.util.Map;
import javax.lang.model.element.*;
import org.jspecify.annotations.Nullable;

class MetaRestateAnnotation {

  private final TypeElement annotation;
  private final ServiceType serviceType;

  private MetaRestateAnnotation(TypeElement annotation, ServiceType serviceType) {
    this.annotation = annotation;
    this.serviceType = serviceType;
  }

  TypeElement getAnnotationTypeElement() {
    return annotation;
  }

  ServiceType getServiceType() {
    return serviceType;
  }

  @Nullable String resolveName(AnnotationMirror mirror) {
    for (Map.Entry<? extends ExecutableElement, ? extends AnnotationValue> entry :
        mirror.getElementValues().entrySet()) {
      if (entry.getKey().getSimpleName().contentEquals("name")) {
        // Got a match, this is the name of the service
        return (String) entry.getValue().getValue();
      }
    }
    // No name parameter found!
    return null;
  }

  static @Nullable MetaRestateAnnotation metaRestateAnnotationOrNull(TypeElement elem) {
    if (elem.getQualifiedName().toString().equals("dev.restate.sdk.annotation.Service")
        || elem.getAnnotation(dev.restate.sdk.annotation.Service.class) != null) {
      return new MetaRestateAnnotation(elem, ServiceType.SERVICE);
    }
    if (elem.getQualifiedName().toString().equals("dev.restate.sdk.annotation.VirtualObject")
        || elem.getAnnotation(dev.restate.sdk.annotation.VirtualObject.class) != null) {
      return new MetaRestateAnnotation(elem, ServiceType.VIRTUAL_OBJECT);
    }
    if (elem.getQualifiedName().toString().equals("dev.restate.sdk.annotation.Workflow")
        || elem.getAnnotation(dev.restate.sdk.annotation.Workflow.class) != null) {
      return new MetaRestateAnnotation(elem, ServiceType.WORKFLOW);
    }
    return null;
  }
}
