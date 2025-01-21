// Copyright (c) 2023 - Restate Software, Inc., Restate GmbH
//
// This file is part of the Restate Java SDK,
// which is released under the MIT license.
//
// You can find a copy of the license in file LICENSE in the root
// directory of this repository or package, or at
// https://github.com/restatedev/sdk-java/blob/main/LICENSE
package dev.restate.sdk.kotlin.gen

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSName
import dev.restate.sdk.endpoint.ServiceType

internal data class MetaRestateAnnotation(
    val annotationName: KSName,
    val serviceType: ServiceType
) {
  fun resolveName(annotated: KSAnnotated): String? =
      annotated.annotations
          .find { it.annotationType.resolve().declaration.qualifiedName == annotationName }
          ?.arguments
          ?.firstOrNull { it -> it.name?.getShortName() == "name" }
          ?.value as String?
}
