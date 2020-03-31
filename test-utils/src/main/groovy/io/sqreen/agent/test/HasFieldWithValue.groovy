package io.sqreen.agent.test

import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeDiagnosingMatcher

import static org.hamcrest.Matchers.anything
import static org.hamcrest.Matchers.equalTo
/* adapted from
 * http://grepcode.com/file/repo1.maven.org/maven2/org.testinfected.hamcrest-matchers/all-matchers/1.5/org/testinfected/hamcrest/jpa/HasFieldWithValue.java */
class HasFieldWithValue<T, U> extends TypeSafeDiagnosingMatcher<T> {

    private final String fieldName
    private final Matcher<? super U> valueMatcher

    HasFieldWithValue(String fieldName, Matcher<? super U> valueMatcher) {
        this.fieldName = fieldName
        this.valueMatcher = valueMatcher
    }

    @Override
    protected boolean matchesSafely(T argument, Description mismatchDescription) {
        boolean exists = fieldExists(argument, mismatchDescription)
        if (!exists) {
            return false
        }

        Object fieldValue = argument.@"$fieldName"
        boolean valueMatches = valueMatcher.matches(fieldValue)
        if (!valueMatches) {
            mismatchDescription.appendText("\"$fieldName\" ")
            valueMatcher.describeMismatch(fieldValue, mismatchDescription)
        }
        valueMatches
    }

    private boolean fieldExists(T argument, Description mismatchDescription) {
        try {
            argument.@"$fieldName"
            true
        } catch (MissingFieldException e) {
            mismatchDescription.appendText("no field \"$fieldName\"")
            false
        }
    }

    void describeTo(Description description) {
        description.appendText("has field \"$fieldName\": ")
        description.appendDescriptionOf(valueMatcher)
    }

    static <T, U> Matcher<T> hasField(String field, Matcher<? super U> value) {
        new HasFieldWithValue<T, U>(field, value)
    }

    static <T, U> Matcher<T> hasField(String field, Object value) {
        new HasFieldWithValue<T, U>(field, equalTo(value))
    }

    static <T> Matcher<T> hasField(String field) {
        new HasFieldWithValue<T, Object>(field, anything())
    }
}
