/*
 * generated by Xtext
 */
package at.bestsolution.persistence.emap.validation;

import java.util.ArrayList;
import java.util.List;
import org.eclipse.emf.ecore.EPackage;

public class AbstractEMapValidator extends org.eclipse.xtext.validation.AbstractDeclarativeValidator {

	@Override
	protected List<EPackage> getEPackages() {
	    List<EPackage> result = new ArrayList<EPackage>();
	    result.add(at.bestsolution.persistence.emap.eMap.EMapPackage.eINSTANCE);
		return result;
	}
}
