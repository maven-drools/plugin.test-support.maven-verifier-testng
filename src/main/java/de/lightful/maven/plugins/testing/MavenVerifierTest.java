/*******************************************************************************
 * Copyright (c) 2009-2011 Ansgar Konermann <konermann@itikko.net>
 *
 * This file is part of the Maven 3 Drools Support Package.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 ******************************************************************************/

package de.lightful.maven.plugins.testing;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.it.util.ResourceExtractor;
import org.testng.IHookCallBack;
import org.testng.IHookable;
import org.testng.ITestResult;
import org.testng.annotations.BeforeMethod;
import org.testng.annotations.Parameters;

import java.io.File;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.fest.assertions.Assertions.assertThat;
import static org.fest.assertions.Fail.fail;

public abstract class MavenVerifierTest implements IHookable {

  private String mavenRepositoryPath;

  @Parameters("repository.integrationtests")
  @BeforeMethod
  public void configureLocalMavenRepository(String mavenRepositoryPath) {
    this.mavenRepositoryPath = mavenRepositoryPath;
  }

  public void run(IHookCallBack callBack, ITestResult testResult) {
    final Method testMethod = testResult.getMethod().getConstructorOrMethod().getMethod();
    final Object testInstance = testResult.getInstance();
    executeMavenGoals(testMethod, testInstance);
    callBack.runTestMethod(testResult);
  }

  private void executeMavenGoals(Method testMethod, Object testInstance) {
    final String testDirectoryName = obtainTestDirectoryName(testMethod);
    File testDirectory;
    Verifier verifier;
    try {
      final File settingsFile = obtainSettingsFile(testMethod);

      testDirectory = ResourceExtractor.simpleExtractResources(getClass(), testDirectoryName);
      verifier = configureVerifier(testMethod, testDirectory, settingsFile);

      String[] goals = obtainGoalsToExecute(testMethod);
      assertThat(goals.length).as("Number of goals to execute").isGreaterThan(0);
      for (String goal : goals) {
        verifier.executeGoal(goal);
      }
      injectVerifierInstance(verifier, testInstance);
    }
    catch (IOException e) {
      fail("Unable to extract integration test resources from directory '" + testDirectoryName + "'.", e);
    }
    catch (VerificationException e) {
      fail("Unable to construct Maven Verifier from project in directory " + testDirectoryName + ".", e);
    }
  }

  private Verifier configureVerifier(Method testMethod, File testDirectory, File settingsFile) throws VerificationException {
    Verifier verifier;
    if (settingsFile != null) {
      verifier = createVerifier(testDirectory.getAbsolutePath(), settingsFile.getAbsolutePath());
    }
    else {
      verifier = createVerifier(testDirectory.getAbsolutePath());
    }
    verifier.setLocalRepo(mavenRepositoryPath);
    verifier.setMavenDebug(obtainMavenDebugFlag(testMethod));
    return verifier;
  }

  private Verifier createVerifier(String testDirectoryName) throws VerificationException {
    return new Verifier(testDirectoryName);
  }

  private Verifier createVerifier(String testDirectoryName, String settingsFileName) throws VerificationException {
    final Verifier verifier = new Verifier(testDirectoryName, settingsFileName);
    verifier.setCliOptions(Arrays.asList("-s ", settingsFileName));
    return verifier;
  }

  private void injectVerifierInstance(Verifier verifier, Object testInstance) {
    final List<Field> injectableFields = findInjectableFields(Verifier.class, testInstance);
    for (Field injectableField : injectableFields) {
      try {
        injectableField.setAccessible(true);
        injectableField.set(testInstance, verifier);
      }
      catch (IllegalAccessException e) {
        throw new RuntimeException("Unable to inject verifier instance into field " + injectableField.getName() + " of " + testInstance, e);
      }
    }
  }

  protected List<Field> findInjectableFields(Class<?> typeOfField, Object instance) {
    final Field[] declaredFields = instance.getClass().getDeclaredFields();
    List<Field> result = new ArrayList<Field>();
    for (Field declaredField : declaredFields) {
      if (declaredField.getType().isAssignableFrom(typeOfField)) {
        result.add(declaredField);
      }
    }
    return result;
  }

  private String obtainTestDirectoryName(Method testMethod) {
    final VerifyUsingProject annotationOnMethod = testMethod.getAnnotation(VerifyUsingProject.class);
    if (annotationOnMethod != null) {
      return annotationOnMethod.value();
    }

    final Class<?> declaringClass = testMethod.getDeclaringClass();
    final VerifyUsingProject annotationOnClass = declaringClass.getAnnotation(VerifyUsingProject.class);
    if (annotationOnClass == null) {
      throw new IllegalArgumentException("No @" + VerifyUsingProject.class.getSimpleName() + " annotation found on " +
                                         "test method or test class. Don't know where to take project definition from.");
    }
    return annotationOnClass.value();
  }

  private boolean obtainMavenDebugFlag(Method testMethod) {
    final MavenDebug annotationOnMethod = testMethod.getAnnotation(MavenDebug.class);
    if (annotationOnMethod != null) {
      return true;
    }
    final Class<?> declaringClass = testMethod.getDeclaringClass();
    final MavenDebug annotationOnClass = declaringClass.getAnnotation(MavenDebug.class);
    return annotationOnClass != null;
  }

  private File obtainSettingsFile(Method testMethod) throws IOException {
    final Class<?> declaringClass = testMethod.getDeclaringClass();
    final SettingsFile annotationOnClass = declaringClass.getAnnotation(SettingsFile.class);
    if (annotationOnClass != null) {
      return ResourceExtractor.simpleExtractResources(getClass(), annotationOnClass.value());
    }
    final SettingsFile annotationOnMethod = testMethod.getAnnotation(SettingsFile.class);
    if (annotationOnMethod != null) {
      return ResourceExtractor.simpleExtractResources(getClass(), annotationOnMethod.value());
    }
    final Annotation[] allAnnotationsOnClass = declaringClass.getAnnotations();
    for (Annotation annotation : allAnnotationsOnClass) {
      final Class<? extends Annotation> annotationType = annotation.annotationType();
      if (annotationType.isAnnotationPresent(SettingsFile.class)) {
        SettingsFile annotationOnMetaAnnotation = annotationType.getAnnotation(SettingsFile.class);
        return ResourceExtractor.simpleExtractResources(annotationType, annotationOnMetaAnnotation.value());
      }
    }
    final Annotation[] allAnnotationsOnMethod = testMethod.getAnnotations();
    for (Annotation annotation : allAnnotationsOnMethod) {
      final Class<? extends Annotation> annotationType = annotation.annotationType();
      if (annotationType.isAnnotationPresent(SettingsFile.class)) {
        SettingsFile annotationOnMetaAnnotation = annotationType.getAnnotation(SettingsFile.class);
        return ResourceExtractor.simpleExtractResources(annotationType, annotationOnMetaAnnotation.value());
      }
    }
    return null;
  }

  private String[] obtainGoalsToExecute(Method testMethod) {
    List<String> goals = new ArrayList<String>();
    final Class<?> declaringClass = testMethod.getDeclaringClass();
    final ExecuteGoals annotationOnClass = declaringClass.getAnnotation(ExecuteGoals.class);
    if (annotationOnClass != null) {
      goals.addAll(Arrays.asList(annotationOnClass.value()));
    }
    final ExecuteGoals annotationOnMethod = testMethod.getAnnotation(ExecuteGoals.class);
    if (annotationOnMethod != null) {
      goals.addAll(Arrays.asList(annotationOnMethod.value()));
    }
    return goals.toArray(new String[goals.size()]);
  }

  protected File expectedOutputFile(Verifier verifier, String relativeFileName) {
    return new File(verifier.getBasedir() + File.separator + relativeFileName);
  }
}
