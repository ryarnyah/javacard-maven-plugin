package com.github.ryarnyah;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public class ReflectionUtils {
    public static <T> T invokeMethodWithArray(Object target, Method method, Object... args) throws MojoFailureException {
        try {
            //noinspection unchecked
            return (T) method.invoke(target, args);
        } catch (IllegalAccessException e) {
            throw new MojoFailureException("Unable to access", e);
        } catch (InvocationTargetException e) {
            throw new MojoFailureException("Unable to call", e.getTargetException());
        }
    }

    public static Method tryGetMethod(Class<?> clazz, String methodName, Class<?>... parameters) {
        try {
            return clazz.getMethod(methodName, parameters);
        } catch (NoSuchMethodException e) {
            return null;
        }
    }
}
