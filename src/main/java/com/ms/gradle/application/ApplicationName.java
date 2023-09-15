package com.ms.gradle.application;

import org.gradle.api.Named;
import org.gradle.api.attributes.Attribute;
import org.gradle.api.attributes.LibraryElements;
import org.gradle.api.attributes.TestSuiteName;

/**
 * Attribute to define the application name. This attribute is usually found on variants that have the
 * {@link LibraryElements} attribute valued at {@value Application#LIBRARY_ELEMENTS_APPLICATION_JAR}.
 *
 * @see TestSuiteName
 */
@SuppressWarnings("PMD.ConstantsInInterface") // This approach is consistent with the rest of Gradle
public interface ApplicationName extends Named {
    Attribute<ApplicationName> APPLICATION_NAME_ATTRIBUTE =
            Attribute.of("com.ms.gradle.application.name", ApplicationName.class);
}
