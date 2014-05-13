package com.github.lemniscate.lib.typed.processor;

import com.github.lemniscate.lib.typed.annotation.InjectTyped;
import org.springframework.aop.framework.AopProxy;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.core.GenericTypeResolver;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.inject.Inject;
import java.lang.reflect.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;


/**
 * @Author dave 5/12/14 8:50 PM
 */
public class InjectTypedAnnotationPostProcessor extends InstantiationAwareBeanPostProcessorAdapter {

    @Inject
    private ApplicationContext ctx;

    @Override
    public Object postProcessBeforeInitialization(final Object bean, String beanName) throws BeansException {

        ReflectionUtils.doWithFields( bean.getClass(), new ReflectionUtils.FieldCallback() {
            @Override
            public void doWith(Field field) throws IllegalArgumentException, IllegalAccessException {
                InjectTyped annotation = field.getAnnotation(InjectTyped.class);
                String[] fieldNames = annotation.value();

                Type type = field.getGenericType();
                if( type instanceof ParameterizedType){
                    ParameterizedType pt = (ParameterizedType) type;
                    Type[] typeArgs = pt.getActualTypeArguments();

                    if( typeArgs == null || typeArgs.length < 1 || typeArgs[0] instanceof TypeVariable ){

                        typeArgs = foo(fieldNames, field.getDeclaringClass(), bean);
                        if( typeArgs.length == 0 ){


                            List<Class<?>> result = new ArrayList<Class<?>>();

                            Class<?> superclass = bean.getClass().getSuperclass();
                            Class<?>[] tempArgs = GenericTypeResolver.resolveTypeArguments(bean.getClass(), superclass);

                            TypeVariable<?>[] superTypes = superclass.getTypeParameters();

                            Assert.isTrue( typeArgs.length <= superTypes.length );
                            for( int i = 0; i < typeArgs.length; i++ ){
                                boolean found = false;
                                for( int k = 0; k < superTypes.length; k++ ){
                                    if( typeArgs[i].equals( superTypes[k]) ){
                                        found = true;
                                        result.add( tempArgs[k] );
                                    }
                                }
                                if( !found ){
                                    throw new RuntimeException("Couldn't match generic type");
                                }
                            }

                            typeArgs = result.toArray(new Type[result.size()]);
                        }


                    }

                    Map<String, ?> impls = ctx.getBeansOfType( field.getType() );
                    for(Object service : impls.values()){
                        Object target = service;
                        Class<?> serviceClass = service.getClass();
                        if( Proxy.isProxyClass(service.getClass()) ){
                            InvocationHandler handler = Proxy.getInvocationHandler(service);
                            if( handler != null ){
                                Field f = ReflectionUtils.findField(handler.getClass(), "advised");
                                ReflectionUtils.makeAccessible(f);
                                Object src = ReflectionUtils.getField(f, handler);
                                ProxyFactory factory = ((ProxyFactory) src);
                                try {
                                    target = factory.getTargetSource().getTarget();
                                    serviceClass = target.getClass();
                                } catch (Exception e) {
                                    throw new RuntimeException("Blah");
                                }
                            }
                        }



                        Assert.isTrue( fieldNames.length == typeArgs.length, "Different number of type args to field names" );



                        for( int i = 0; i < fieldNames.length; i++  ){
                            String fieldName = fieldNames[i];
                            Field classField = ReflectionUtils.findField(serviceClass, fieldName);
                            classField.setAccessible(true);
                            Class<?> classFieldValue = (Class<?>) ReflectionUtils.getField(classField, target);
                            if( ((Class<?>) typeArgs[i]).isAssignableFrom(classFieldValue) ){
                                field.setAccessible(true);
                                field.set(bean, service);
                                return;
                            }
                        }
                    }
                }

                if( annotation.required() ){
                    String msg = "Couldn't find a match to inject into " + bean.getClass().getSimpleName() + "#" + field.getName();
                    throw new InjectTypedMatchNotFound(msg);
                }
            }
        }, FILTER);

        return bean;
    }

    private Type[] foo(String[] fieldNames, Class<?> serviceClass, Object hasFields){
        Type[] typeArgs = new Type[fieldNames.length];
        for( int i = 0; i < fieldNames.length; i++  ){
            String fieldName = fieldNames[i];
            Field classField = ReflectionUtils.findField(serviceClass, fieldName);
            classField.setAccessible(true);
            Class<?> classFieldValue = (Class<?>) ReflectionUtils.getField(classField, hasFields);
            typeArgs[i] = classFieldValue;
        }
        return typeArgs;
    }

    private static final ReflectionUtils.FieldFilter FILTER = new ReflectionUtils.FieldFilter() {
        @Override
        public boolean matches(Field field) {
            return field.isAnnotationPresent(InjectTyped.class);
        }
    };

    public static class InjectTypedMatchNotFound extends RuntimeException{
        public InjectTypedMatchNotFound(String message) {
            super(message);
        }

        public InjectTypedMatchNotFound(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
