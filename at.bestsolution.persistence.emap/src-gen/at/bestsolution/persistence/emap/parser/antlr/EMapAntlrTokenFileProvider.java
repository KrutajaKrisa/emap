/*
 * generated by Xtext
 */
package at.bestsolution.persistence.emap.parser.antlr;

import java.io.InputStream;
import org.eclipse.xtext.parser.antlr.IAntlrTokenFileProvider;

public class EMapAntlrTokenFileProvider implements IAntlrTokenFileProvider {
	
	@Override
	public InputStream getAntlrTokenFile() {
		ClassLoader classLoader = getClass().getClassLoader();
    	return classLoader.getResourceAsStream("at/bestsolution/persistence/emap/parser/antlr/internal/InternalEMap.tokens");
	}
}
