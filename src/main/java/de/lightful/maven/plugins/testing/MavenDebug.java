/*******************************************************************************
 * Copyright (c) 2009-2012 Ansgar Konermann
 *
 * This file is part of the "Maven 3 Drools Support" Package.
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
 ******************************************************************************/

package de.lightful.maven.plugins.testing;

import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.METHOD;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

/**
 * Will cause {@link MavenVerifierTest} to run Maven in debug mode either for all test methods (if used on class level) or for the
 * annotated test method (if used on test method level).
 */
@Retention(RUNTIME)
@Target({TYPE, METHOD})
public @interface MavenDebug {

}
