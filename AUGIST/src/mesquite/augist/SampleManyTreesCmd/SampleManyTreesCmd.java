package mesquite.augist.SampleManyTreesCmd;


/* Mesquite source code.  Copyright 1997-2008 W. Maddison and D. Maddison. Module by J.C. Oliver
Version 2.5, June 2008.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
*/

import mesquite.lib.*;
import mesquite.trees.SampleManyTreesFromFile.*;

/*Almost identical to it's superclass, this module depends on employer to dictate how many trees to sample 
 * (defaults to 1 if number is not provided).*/
public class SampleManyTreesCmd extends SampleManyTreesFromFile {
	MesquiteInteger pos = new MesquiteInteger(0);
/*.................................................................................................................*/
	protected boolean additionStartJobItems(){
		addMenuItem("File for Sample Trees From Separate...", makeCommand("setFilePath",  this));
		numTreesToSample = 1; //Employers should use setNumTreesToSample method below to change this.

		addMenuItem("Number of Trees to Sample...", makeCommand("setNumTreesToSample",  this));

		addMenuItem("Number of Trees to Ignore...", makeCommand("setStartTreesToIgnore",  this));
		if (!MesquiteThread.isScripting()){
			if (numTreesInTreeBlock>0 && MesquiteInteger.isCombinable(numTreesInTreeBlock)) {
				numStartTreesToIgnore =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Ignore", "Number of trees to ignore (out of " + numTreesInTreeBlock + " total trees) from start of file:", numStartTreesToIgnore, 0, numTreesInTreeBlock, true);
				}
				else {
					numStartTreesToIgnore =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Ignore", "Number of trees to ignore from start of  file:", numStartTreesToIgnore, 0, MesquiteInteger.infinite, true);
				}
			if (!MesquiteInteger.isCombinable(numStartTreesToIgnore))
					return false;
			else
				setTreesToSample(numTreesToSample);
		}
		return true;
	}
	/*.................................................................................................................*/
	/**Sets the number of trees to sample to the passed int numToSample (if combinable).  Provides employer with additional control over
	 * module's behavior.*/
	public void setNumTreesToSample(int numToSample){
		if(MesquiteInteger.isCombinable(numToSample))
			numTreesToSample = numToSample;
	}
	/*.................................................................................................................*/
	/**Resets the trees to be sampled, i.e. resets the bits so a new (different) sample is drawn when fillTreeBlock method is called.*/
	public void resetTreesToSample(){
		setTreesToSample(numTreesToSample);
	}
	/*.................................................................................................................*/
//	public void additionalSnapshot(Snapshot snapshot) {
//		snapshot.addLine("setFilePath " + getFilePath());//using getFilePath() method of superclass ManyTreesFromFileLib
//	}
	/*.................................................................................................................*/
	public boolean additionalDoCommands(String commandName, String arguments, CommandChecker checker) {
		if (checker.compare(this.getClass(), "Specifies the number of trees to sample", "[number of trees]", commandName, "setNumTreesToSample")) {
			pos.setValue(0);
			int num = MesquiteInteger.fromString(arguments, pos);
			if (!MesquiteInteger.isCombinable(num)&& !MesquiteThread.isScripting()){
				int nt = numTreesToSample;
				if (!MesquiteInteger.isCombinable(nt))
					nt=100;
				if (numTreesInTreeBlock>0 && MesquiteInteger.isCombinable(numTreesInTreeBlock)) {
					num =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Sample", "Number of Trees to Sample (out of " + numTreesInTreeBlock + " total trees) from file:", nt, 0, numTreesInTreeBlock, true);
				}
				else {
					num =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Sample", "Number of trees to sample from file:", nt, 0, MesquiteInteger.infinite, true);
				}
			}
			if (MesquiteInteger.isCombinable(num)) {
				numTreesToSample = num;
				setTreesToSample(numTreesToSample);
				parametersChanged();
			}
			return true;
		}
		else if (checker.compare(this.getClass(), "Specifies the number of trees to ignore from the start of the file", "[number of trees]", commandName, "setStartTreesToIgnore")) {
			pos.setValue(0);
			int num = MesquiteInteger.fromString(arguments, pos);
			if (!MesquiteInteger.isCombinable(num)&& !MesquiteThread.isScripting()){
				if (numTreesInTreeBlock>0 && MesquiteInteger.isCombinable(numTreesInTreeBlock)) {
					num =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Ignore", "Number of trees to ignore (out of " + numTreesInTreeBlock + " total trees) from start of file:", numStartTreesToIgnore, 0, numTreesInTreeBlock, true);
				}
				else {
					num =MesquiteInteger.queryInteger(containerOfModule(), "Number of Trees to Ignore", "Number of trees to ignore from start of  file:", numStartTreesToIgnore, 0, MesquiteInteger.infinite, true);
				}
			}
			if (MesquiteInteger.isCombinable(num)) {
				numStartTreesToIgnore = num;
				setTreesToSample(numTreesToSample);
				parametersChanged();
			}
			return true;
		}
		return false;
	}
/*.................................................................................................................*/
	public String getName() {
	return "Randomly Sample Trees from Separate NEXUS File";
	}
/*.................................................................................................................*/
	public String getNameForMenuItem() {
	return "Randomly Sample Trees from Separate File...";
	}
/*.................................................................................................................*/
	public String getExplanation() {
	return "Very similar to 'Sample Trees From Separate NEXUS File', but has some differences in internal control.  Unless specifically instructed, users should probably use 'Sample Trees From Separate NEXUS File' instead of this module.  Supplies randomly-sampled trees directly from a file, without bringing the contained tree block entirely into memory.  This allows much larger blocks of trees to be used within constraints of memory, but will make some calculations slower.  This module does not know how many trees are in the file, and hence may attempt to read files beyond the number in the file.";
	}
}
