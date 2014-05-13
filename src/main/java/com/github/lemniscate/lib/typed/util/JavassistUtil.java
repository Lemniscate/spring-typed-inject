package com.github.lemniscate.lib.typed.util;

import com.github.lemniscate.lib.typed.annotation.BeanMarker;
import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.springframework.context.annotation.Bean;
import org.springframework.util.Assert;

public class JavassistUtil {

    public Class<?> createTypedSubclass(Class<?> baseImpl, Class<?>[] classes) {
        try{
            ClassPool pool = ClassPool.getDefault();

            StringBuilder name = new StringBuilder( baseImpl.getSimpleName() );

            // Generate the generic signature
            String baseImplSig = baseImpl.getName().replace(".", "/");

            // TODO find out why we have the root Object in there...
            StringBuilder sig = new StringBuilder("Ljava/lang/Object;L" + baseImplSig + "<");
            for(int i = 0; i < classes.length; i++){
                Class<?> c = classes[i];
                pool.appendClassPath( new ClassClassPath(c));
                CtClass ct = pool.get( c.getName() );
                sig.append("L")
                    .append( ct.getName().replace('.', '/') )
                    .append(";");

                // while we're at it, append the class types to the name
                name.append("_")
                    .append(c.getSimpleName());
            }
            sig.append(">;");

            // let's tag on a Impl at the end of the name, just in case
            name.append("_Impl");

            // Generate our actual base class
            CtClass ctBaseImpl = pool.get(baseImpl.getName());
            CtClass concrete = pool.makeClass( name.toString() );
            concrete.setSuperclass(ctBaseImpl);

            // apply our generic signature
            concrete.setGenericSignature(sig.toString());

            // Add a default constructor
            CtConstructor constructor = CtNewConstructor.defaultConstructor(concrete);
            constructor.setBody("{}");
            concrete.addConstructor(constructor);

            // handle @BeanMarker definitions
            ConstPool constPool = concrete.getClassFile().getConstPool();
            for(CtMethod method : ctBaseImpl.getMethods() ){
                if( method.getAnnotation(BeanMarker.class) != null){
                    CtMethod newMethod = CtNewMethod.copy(method, concrete, null);


                    AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                    Annotation annotation = new Annotation(Bean.class.getName(), constPool);
                    ArrayMemberValue names = new ArrayMemberValue(new StringMemberValue(constPool), constPool);
                    names.setValue(new MemberValue[]{new StringMemberValue(name.toString() + "_" + newMethod.getName(), constPool)});
                    annotation.addMemberValue("name", names);
                    attr.setAnnotation(annotation);
                    newMethod.getMethodInfo().addAttribute( attr );

                    concrete.addMethod(newMethod);

                }
            }

            Class<?> result = concrete.toClass();
            return result;
        }catch(Exception e){
            throw new RuntimeException("Failed to create subclass: " + e.getMessage(), e);
        }
    }
}
