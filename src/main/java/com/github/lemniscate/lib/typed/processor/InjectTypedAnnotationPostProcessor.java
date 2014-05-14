package com.github.lemniscate.lib.typed.processor;

import com.github.lemniscate.lib.typed.annotation.InjectTyped;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.core.GenericTypeResolver;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.*;
import java.util.Map;

public class InjectTypedAnnotationPostProcessor extends InstantiationAwareBeanPostProcessorAdapter
            implements PriorityOrdered, ApplicationContextAware {

    // TODO lots of cleanup...


    private ApplicationContext ctx;

    @Override
    public Object postProcessBeforeInitialization(final Object bean, String beanName) throws BeansException {
        ReflectionUtils.FieldCallback fieldHandler = new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                processField(bean, field);
            }
        };

        ReflectionUtils.doWithFields( bean.getClass(), fieldHandler, FILTER);
        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE - 1;
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
        this.ctx = applicationContext;
    }

    public static class InjectTypedMatchNotFound extends RuntimeException{
        public InjectTypedMatchNotFound(String message) {
            super(message);
        }
    }

    public void processField(Object bean, Field field) throws IllegalArgumentException, IllegalAccessException{
        InjectTyped annotation = field.getAnnotation(InjectTyped.class);
        String[] fieldNames = annotation.value();

        Type type = field.getGenericType();
        if( type instanceof ParameterizedType){
            ParameterizedType pt = (ParameterizedType) type;
            Type[] typeArgs = pt.getActualTypeArguments();

            // if no concrete types, try the reverse lookup fields
            if( typeArgs == null || typeArgs.length < 1 || typeArgs[0] instanceof TypeVariable ){

                // if reverse lookup fields are empty, attempt the field names on the candidate object
                // (meaning the candidate and the object needing injection have the same type hints)
                String[] lookupFields = annotation.reverseLookupFields();
                String[] lookupFieldNames = lookupFields.length == 0 ? fieldNames : lookupFields;
                typeArgs = resolveTypeParameters(lookupFieldNames, field.getDeclaringClass(), bean);
            }

            Map<String, ?> implementations = ctx.getBeansOfType( field.getType() );
            for(Object impl : implementations.values()){
                Class<?> implClass = impl.getClass();


                // TODO clean up this block
                // look for a quick match on a concrete implementation
                Class<?>[] implTypes = GenericTypeResolver.resolveTypeArguments(impl.getClass(), field.getType());
                if( implTypes != null && implTypes.length >= typeArgs.length ){
                    boolean matched = true;
                    for(int i = 0; i < typeArgs.length; i++){
                        if( !typeArgs[i].equals( implTypes[i])){
                            matched = false;
                            break;
                        }
                    }
                    if( matched ){
                        field.setAccessible(true);
                        field.set(bean, impl);
                        return;
                    }
                }

                // Handle proxied objects
                Object target = impl;
                if( Proxy.isProxyClass(impl.getClass()) ){
                    try {
                        InvocationHandler handler = Proxy.getInvocationHandler(impl);
                        if( handler != null ){
                            Field f = ReflectionUtils.findField(handler.getClass(), "advised");
                            ReflectionUtils.makeAccessible(f);
                            Object src = ReflectionUtils.getField(f, handler);
                            ProxyFactory factory = ((ProxyFactory) src);

                            target = factory.getTargetSource().getTarget();
                            implClass = target.getClass();
                        }
                    } catch (Exception e) {
                        throw new RuntimeException("Failed getting a proxied target...", e);
                    }
                }

                Assert.isTrue( fieldNames.length == typeArgs.length, "Different number of type args to field names" );

                if( matchFields(fieldNames, implClass, target, typeArgs) ){
                    field.setAccessible(true);
                    field.set(bean, impl);
                    return;
                }
            }

            if( annotation.required() ){
                String msg = "Couldn't find a match to inject into " + bean.getClass().getSimpleName() + "#" + field.getName();
                throw new InjectTypedMatchNotFound(msg);
            }
        }else{
            throw new IllegalStateException("Shouldn't be able to get here...");
        }


    }

    /**
     * Looks for type hints on the supplied instance with the specified fieldNames.
     */
    private Type[] resolveTypeParameters(String[] fieldNames, Class<?> clazz, Object instance){
        Type[] typeArgs = new Type[fieldNames.length];
        for( int i = 0; i < fieldNames.length; i++  ){
            String fieldName = fieldNames[i];
            Field classField = ReflectionUtils.findField(clazz, fieldName);
            classField.setAccessible(true);
            Class<?> classFieldValue = (Class<?>) ReflectionUtils.getField(classField, instance);
            typeArgs[i] = classFieldValue;
        }
        return typeArgs;
    }

    /**
     * Verifies that the fields on a given instance match typeArgs
     */
    private boolean matchFields(String[] fieldNames, Class<?> implClass, Object target, Type[] typeArgs){
        for( int i = 0; i < fieldNames.length; i++  ){
            String fieldName = fieldNames[i];
            Field classField = ReflectionUtils.findField(implClass, fieldName);
            if( classField == null ){
                return false;
            }

            classField.setAccessible(true);
            Class<?> classFieldValue = (Class<?>) ReflectionUtils.getField(classField, target);
            if( ! ((Class<?>) typeArgs[i]).isAssignableFrom(classFieldValue) ){
                return false;
            }
        }
        return true;
    }

    private static final ReflectionUtils.FieldFilter FILTER = new ReflectionUtils.FieldFilter() {
        @Override
        public boolean matches(Field field) {
            return field.isAnnotationPresent(InjectTyped.class);
        }
    };

}
