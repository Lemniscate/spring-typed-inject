package com.github.lemniscate.lib.typed.util;

import com.github.lemniscate.lib.typed.annotation.BeanMarker;
import com.github.lemniscate.lib.typed.processor.InjectTypedAnnotationPostProcessor;
import javassist.*;
import javassist.bytecode.AnnotationsAttribute;
import javassist.bytecode.ClassFile;
import javassist.bytecode.ConstPool;
import javassist.bytecode.annotation.Annotation;
import javassist.bytecode.annotation.ArrayMemberValue;
import javassist.bytecode.annotation.MemberValue;
import javassist.bytecode.annotation.StringMemberValue;
import org.springframework.context.annotation.Bean;

import javax.inject.Inject;
import java.util.concurrent.atomic.AtomicLong;

public class JavassistUtil {

    private AtomicLong idGen = new AtomicLong();

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
            name.append(idGen.incrementAndGet());

            // Generate our actual base class
            CtClass ctBaseImpl = pool.get(baseImpl.getName());
            CtClass impl = pool.makeClass( name.toString() );
            impl.setSuperclass(ctBaseImpl);

            // apply our generic signature
            impl.setGenericSignature(sig.toString());

            // Add a default constructor
            CtConstructor constructor = CtNewConstructor.defaultConstructor(impl);
            constructor.setBody("{}");
            impl.addConstructor(constructor);

            // useful stuff...
            ClassFile classFile = impl.getClassFile();
            ConstPool constPool = classFile.getConstPool();


            // TODO this is hacky... find another way?
            // add the post processor
            CtClass ctProcessor = pool.get(InjectTypedAnnotationPostProcessor.class.getName());
            CtField processorField = new CtField(ctProcessor, "_itapp", impl);
            AnnotationsAttribute processorInjectAttr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
            Annotation processorInjectAnnotation = new Annotation(Inject.class.getName(), constPool);
            processorInjectAttr.setAnnotation(processorInjectAnnotation);
            processorField.getFieldInfo().addAttribute(processorInjectAttr);
            impl.addField(processorField);

            CtField processedField = new CtField(CtPrimitiveType.booleanType, "_processed", impl);
            impl.addField(processedField);


            // handle @BeanMarker definitions
            for(CtMethod method : ctBaseImpl.getMethods() ){
                if( method.getAnnotation(BeanMarker.class) != null){
                    CtMethod newMethod = CtNewMethod.copy(method, impl, null);


                    AnnotationsAttribute attr = new AnnotationsAttribute(constPool, AnnotationsAttribute.visibleTag);
                    Annotation annotation = new Annotation(Bean.class.getName(), constPool);
                    ArrayMemberValue names = new ArrayMemberValue(new StringMemberValue(constPool), constPool);
                    names.setValue(new MemberValue[]{new StringMemberValue(name.toString() + "_" + newMethod.getName(), constPool)});
                    annotation.addMemberValue("name", names);
                    attr.setAnnotation(annotation);
                    newMethod.getMethodInfo().addAttribute( attr );

                    impl.addMethod(newMethod);


                    newMethod.insertBefore("{ if( this._itapp != null && !_processed ){ this._itapp.postProcessBeforeInitialization(this, \"No Handle on bean name :( \"); _processed = true; } }");
                }
            }

            Class<?> result = impl.toClass();
            return result;
        }catch(Exception e){
            throw new RuntimeException("Failed to create subclass: " + e.getMessage(), e);
        }
    }

}
