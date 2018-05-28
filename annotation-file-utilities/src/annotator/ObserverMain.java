package annotator;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import annotator.specification.IndexFileSpecification;
import scenelib.annotations.Annotation;
import scenelib.annotations.AnnotationBuilder;
import scenelib.annotations.AnnotationFactory;
import scenelib.annotations.el.AClass;
import scenelib.annotations.el.AElement;
import scenelib.annotations.el.AMethod;
import scenelib.annotations.el.AScene;
import scenelib.annotations.el.AnnotationDef;
import scenelib.annotations.field.AnnotationFieldType;
import annotator.find.Insertion;
import annotator.find.Insertions;
import org.plumelib.util.UtilPlume;

public class ObserverMain {
    public static void main(String[] args) {
        Path workingDirectory = Paths.get(args[0]);
        Path outputFile = Paths.get(args[1]);
        List<String> sideEffectFreeMethods = new ArrayList<String>();
        try {
            walkClassFiles(workingDirectory, sideEffectFreeMethods);
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

        try {
            BufferedWriter outputWriter = new BufferedWriter(new FileWriter(outputFile.toFile()));
            for (String method : sideEffectFreeMethods) {
                outputWriter.write(method);
                outputWriter.newLine();
            }
            outputWriter.flush();
            outputWriter.close();
        } catch(IOException e) {
            e.printStackTrace();
            System.exit(1);
        }

    }

    public static void walkClassFiles(Path root,
                                      List<String> outSideEffectFreeMethods) throws IOException {
        Stream<Path> paths = Files.walk(root).filter(Files::isRegularFile);
        paths.forEach(filePath -> {
            if (filePath.toString().endsWith(".jaif")) {
                try {
                    readFile(filePath, outSideEffectFreeMethods);
                } catch (FileNotFoundException ex) {
                    throw(new RuntimeException(ex));
                } catch (IOException ex) {
                    throw(new RuntimeException(ex));
                }
            }
        });
        paths.close();
    }

    public static void readFile(Path filePath, List<String> outMethods) throws FileNotFoundException, IOException {
        IndexFileSpecification ifs = new IndexFileSpecification(filePath.toString());
        AScene scene = ifs.getScene();
        ifs.parse();
        for (Map.Entry<String, AClass> entry : scene.classes.entrySet()) {
            AClass cls = entry.getValue();
            for (Map.Entry<String, AMethod> m : cls.methods.entrySet()) {
            	
            	// The commented out code might be too heavy for our purposes (?)
            	//	besides the fact I haven't got it working yet...
            	/*AnnotationDef aDef = new AnnotationDef("@org.checkerframework.dataflow.qual.Pure");
            	aDef.setFieldTypes(new HashMap<String, AnnotationFieldType>());
            	Annotation annotation = AnnotationFactory.saf.beginAnnotation(aDef).finish();*/
            	boolean sideEffectFree = false;
            	for (Annotation a : m.getValue().tlAnnotationsHere) {
            		if (a.def.name.equals("org.checkerframework.dataflow.qual.Pure")) {
            			sideEffectFree = true;
            			break;
            		} else if (a.def.name.equals("org.checkerframework.dataflow.qual.SideEffectFree")) {
            			sideEffectFree = true;
            			break;
            		}
            	}
            	if (!sideEffectFree)
            		continue;
            	String JVMLmethodSignature = m.getValue().methodName;
            	int arglistStartIndex = JVMLmethodSignature.indexOf("(");
            	int arglistEndIndex = JVMLmethodSignature.indexOf(")") + 1;
            	String methodName = JVMLmethodSignature.substring(0, arglistStartIndex);
            	
            	if (methodName.equals("<init>"))
            		continue; // Ignore constructors (?)
            	
            	String fullyQualifiedMethodName = cls.className;
            	String fullyQualifiedArgs = UtilPlume.arglistFromJvm(JVMLmethodSignature.substring(arglistStartIndex, arglistEndIndex));
            	String fullyQualifiedSignature = fullyQualifiedMethodName + "." + methodName + fullyQualifiedArgs;
            			
            	outMethods.add(fullyQualifiedSignature);
            }
        }
    }
}
