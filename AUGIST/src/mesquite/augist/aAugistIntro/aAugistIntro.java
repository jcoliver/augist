package mesquite.augist.aAugistIntro;

import mesquite.lib.*;
import mesquite.lib.duties.*;

public class aAugistIntro extends PackageIntro{

	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		return true;
	}
	/*.................................................................................................................*/
	public Class getDutyClass(){
		return aAugistIntro.class;
	}
	/*.................................................................................................................*/
	public String getName() {
		return "AUGIST package";
	}
	/*.................................................................................................................*/
	public String getPackageName() {
		return "AUGIST package";
	}
	/*.................................................................................................................*/
	public String getExplanation(){
		return "The AUGIST package includes modules for accommodating uncertainty in tree inference procedures.";
	}
	/*.................................................................................................................*/
	/** Returns citation for a package of modules*/
	public String getPackageCitation(){
		return "Oliver, J.C. 2010.  AUGIST: Accommodating Uncertainty in Genealogies while Inferring Species Trees.";
	}
	/*.................................................................................................................*/
	/** Returns whether there is a splash banner*/
	public boolean hasSplash(){
		return true; 
	}
	/*.................................................................................................................*/
	/** Returns whether package is built-in (comes with default install of Mesquite)*/
	public boolean isBuiltInPackage(){
		return false;
	}

}
