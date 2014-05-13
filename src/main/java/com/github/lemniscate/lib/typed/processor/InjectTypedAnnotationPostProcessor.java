package com.github.lemniscate.lib.typed.processor;

import com.github.lemniscate.lib.typed.annotation.InjectTyped;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.InstantiationAwareBeanPostProcessorAdapter;
import org.springframework.context.ApplicationContext;
import org.springframework.util.Assert;
import org.springframework.util.ReflectionUtils;

import javax.inject.Inject;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
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
                Type type = field.getGenericType();
                if( type instanceof ParameterizedType){
                    ParameterizedType pt = (ParameterizedType) type;
                    Type[] typeArgs = pt.getActualTypeArguments();


                    Map<String, ?> impls = ctx.getBeansOfType( field.getType() );
                    for(Object service : impls.values()){
                        Class<?> serviceClass = service.getClass();
                        String[] fieldNames = annotation.value();
                        Assert.isTrue( fieldNames.length == typeArgs.length, "Different number of type args to field names" );
                        for( int i = 0; i < fieldNames.length; i++  ){
                            String fieldName = fieldNames[i];
                            Field classField = ReflectionUtils.findField(serviceClass, fieldName);
                            classField.setAccessible(true);
                            Class<?> classFieldValue = (Class<?>) ReflectionUtils.getField(classField, service);
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
