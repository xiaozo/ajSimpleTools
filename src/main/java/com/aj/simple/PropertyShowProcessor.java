package com.aj.simple;

import com.sun.source.tree.Tree;
import com.sun.tools.javac.api.JavacTrees;
import com.sun.tools.javac.code.Flags;
import com.sun.tools.javac.processing.JavacProcessingEnvironment;
import com.sun.tools.javac.tree.JCTree;
import com.sun.tools.javac.tree.JCTree.JCVariableDecl;
import com.sun.tools.javac.tree.TreeMaker;
import com.sun.tools.javac.tree.TreeTranslator;
import com.sun.tools.javac.util.*;

import javax.annotation.processing.*;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.lang.model.util.Elements;
import java.util.Set;
@SupportedAnnotationTypes({"com.aj.simple.PropertyShow"})
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PropertyShowProcessor extends AbstractProcessor {

    private Messager messager;
    private JavacTrees trees;
    private TreeMaker treeMaker;
    private Names names;

    private Elements elementsUtils;


    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.messager = processingEnv.getMessager();
        this.trees = JavacTrees.instance(processingEnv);
        Context context = ((JavacProcessingEnvironment) processingEnv).getContext();
        this.treeMaker = TreeMaker.instance(context);
        this.names = com.sun.tools.javac.util.Names.instance(context);

        elementsUtils = processingEnv.getElementUtils();

    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        Set<? extends Element> set = roundEnv.getElementsAnnotatedWith(PropertyShow.class);

        set.forEach(element -> {
            JCTree jcTree = trees.getTree(element);
            TypeElement enClosingElement = (TypeElement)element.getEnclosingElement();
            JCTree.JCClassDecl jcClassDecl = (JCTree.JCClassDecl)trees.getTree(enClosingElement);

            jcTree.accept(new TreeTranslator() {
                @Override
                public void visitVarDef(JCVariableDecl var1) {

                    if (var1.getKind().equals(Tree.Kind.VARIABLE)) {
                        //添加方法属性
                        String fieldName = var1.getName().toString();
                        fieldName = fieldName + "_name";
                        jcClassDecl.defs = jcClassDecl.defs.prepend(treeMaker.VarDef(treeMaker.Modifiers(Flags.PUBLIC),names.fromString(fieldName),memberAccess("java.lang.String"),null));

                        //增加方法
                        String obj = element.getAnnotation(PropertyShow.class).obj();
                        String meth = element.getAnnotation(PropertyShow.class).meth();

                        ListBuffer<JCTree.JCStatement> statements = new ListBuffer<>();
                        JCTree.JCExpression param = treeMaker.Select(treeMaker.Ident(names.fromString("this")), var1.getName());
                        statements.append(treeMaker.Return( treeMaker.Apply(List.of(var1.vartype),memberAccess(obj+"."+meth),List.of(param))));
                        JCTree.JCBlock body = treeMaker.Block(0, statements.toList());
                        //get
                        JCTree.JCMethodDecl newGetMethodDecl = treeMaker.MethodDef(treeMaker.Modifiers(Flags.PUBLIC), getNewMethodName(fieldName), memberAccess("java.lang.String"), List.nil(), List.nil(), List.nil(), body, null);
                        jcClassDecl.defs = jcClassDecl.defs.prepend(newGetMethodDecl);
                    }
                    super.visitVarDef(var1);


                }

            });
        });



        return true;
    }


    private JCTree.JCExpression memberAccess(String components) {
        String[] componentArray = components.split("\\.");
        JCTree.JCExpression expr = treeMaker.Ident(getNameFromString(componentArray[0]));
        for (int i = 1; i < componentArray.length; i++) {
            expr = treeMaker.Select(expr, getNameFromString(componentArray[i]));
        }
        return expr;
    }

    private Name getNameFromString(String s) { return names.fromString(s); }

    private String getClassName(TypeElement enClosingElement, String packageName) {
        int packageLength = packageName.length()+1;
        return enClosingElement.getQualifiedName().toString().substring(packageLength).replace(".","$");
    }

    private String getPackageName(TypeElement enClosingElement) {
        return elementsUtils.getPackageOf(enClosingElement).getQualifiedName().toString();
    }

    private Name getNewMethodName( String s) {
        return names.fromString("get" + s.substring(0, 1).toUpperCase() + s.substring(1, s.length()));
    }



}
