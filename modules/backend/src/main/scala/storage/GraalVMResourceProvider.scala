/*
 * Copyright 2022 github.com/2m/rallyeye/contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package rallyeye
package storage

import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.FileSystems
import java.util.Collection
import java.util.stream.Collectors
import java.util as ju

import scala.jdk.CollectionConverters.*
import scala.util.Using

import org.flywaydb.core.api.Location
import org.flywaydb.core.api.ResourceProvider
import org.flywaydb.core.api.resource.LoadableResource
import org.flywaydb.core.internal.resource.classpath.ClassPathResource

// https://github.com/flyway/flyway/issues/2927#issuecomment-1226955467
// https://github.com/mhalbritter/flyway-native-image/blob/main/src/main/java/flywaynativeimage/GraalVMResourceProvider.java

class GraalVMResourceProvider(locations: List[Location]) extends ResourceProvider:
  import GraalVMResourceProvider.*

  def getResource(name: String) =
    if getClassLoader.getResource(name) == null then null
    else new ClassPathResource(null, name, getClassLoader, StandardCharsets.UTF_8)

  def getResources(prefix: String, suffixes: Array[String]): Collection[LoadableResource] =
    Using.resource(FileSystems.newFileSystem(URI.create("resource:/"), ju.Map.of())): system =>
      locations.asJava.stream
        .flatMap: location =>
          Using
            .resource(Files.walk(system.getPath(location.getPath))) { files =>
              files
                .filter(Files.isRegularFile(_))
                .filter(_.getFileName.toString.startsWith(prefix))
                .filter(file => suffixes.toList.exists(suffix => file.getFileName.toString.endsWith(suffix)))
                .map(file => new ClassPathResource(null, file.toString, getClassLoader, StandardCharsets.UTF_8))
                .collect(Collectors.toList)
            }
            .stream
        .collect(Collectors.toList)

object GraalVMResourceProvider:
  def getClassLoader = classOf[GraalVMResourceProvider].getClassLoader
