/* Mesquite source code.  Copyright 1997-2007 W. Maddison and D. Maddison. Module by J.C. Oliver.
Version 1.0, May 2012.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */
package mesquite.augist.SampleTreesFromDirectory;

import java.io.*;
import java.util.*;
//import mesquite.augist.SampleManyTreesCmd.*;
import mesquite.augist.SampleManyTreesCmd.SampleManyTreesCmd;
import mesquite.augist.SampleOneTreeFromFile.SampleOneTreeFromFile;
import mesquite.lib.*;
import mesquite.lib.duties.*;
import mesquite.trees.lib.*;

//TODO: REQUIRED A CHANGE TO BasicFileCoordinator's getNEXUSFileForReading method because passed file path was getting chopped up by parser.
public class SampleTreesFromDirectory extends TreeBlockSource{
	Taxa currentTaxa = null;
	Taxa preferredTaxa = null;
	TreeVector currentTreeBlock = null;
	TreeVector lastUsedTreeBlock = null;
	int currentTreeBlockIndex = -1;
	File directory;
	static String previousDirectory = null;
	Vector fillerTasks;

	public String getName() {
		return "Sample Trees from Directory";
	}
	/*................................................................................................*/
	public String getNameForMenuItem() {
		return "Sample Trees from Directory...";
	}
	/*................................................................................................*/
	public String getExplanation(){
		return "Samples a single tree from each tree file in user-supplied directory.";
	}

	/*................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		addMenuItem("Directory for Sample Trees From Directory...", makeCommand("setDirPath",  this));
		fillerTasks = new Vector();
		if(!MesquiteThread.isScripting()){//enclosed in conditional to avoid hiring query when opening file; should be handled by snapshot/doCommand when file is opened.
			String directoryPath = MesquiteFile.chooseDirectory("Choose directory containing tree files:", previousDirectory); //MesquiteFile.saveFileAsDialog("Base name for files (files will be named <name>1.nex, <name>2.nex, etc.)", baseName);

			if (StringUtil.blank(directoryPath)){ //TODO: clean this up?
				return false;
			} else {
				directory = new File(directoryPath);
				previousDirectory = directory.getParent();
				if (directory.exists() && directory.isDirectory()) {
					return hireFillers(directoryPath);
			}
			}
		}
		return true;//Add something to make sure directory paths are set up correctly, when not scripting...
	}

	/*................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		//TODO: add setDirPath commands here
		return super.doCommand(commandName, arguments, checker);
	}
	/*................................................................................................*/
	@Override
	public void initialize(Taxa taxa) {
		setPreferredTaxa(taxa);
//		for (int sourceCount = 0; sourceCount<numSources; sourceCount++){
//			if(fillerTasks[sourceCount]!=null)
//				fillerTasks[sourceCount].initialize(taxa);
//		}
	}
	/*................................................................................................*/
	public void setPreferredTaxa(Taxa taxa) { //Copied from SampleTreesMultSources
		if (taxa !=currentTaxa) {
			if (currentTaxa!=null)
				currentTaxa.removeListener(this);
			currentTaxa = taxa;
			currentTaxa.addListener(this);
		}
	}
	/*................................................................................................*/
	private boolean hireFillers(String directoryPath){
		String[] files = directory.list();
		String treePath;
		for(int i = 0; i < files.length; i++){
			if(files[i] != null){
				String fileLowerCase = files[i].toLowerCase();
				if(fileLowerCase.endsWith(".nex") || fileLowerCase.endsWith(".nexus")){
					treePath = directoryPath + MesquiteFile.fileSeparator + files[i];
					File treeFile = new File(treePath);
					String treeFileName = treeFile.getName();
					if (StringUtil.blank(treeFileName)) {
						return false;
					}
					TreeBlockFiller newFiller;
					newFiller = (TreeBlockFiller)hireNamedEmployee(TreeBlockFiller.class, "#SampleOneTreeFromFile");// treePath);
					if(newFiller != null){
						if(((SampleOneTreeFromFile)newFiller).setFilePath(treePath) && ((SampleOneTreeFromFile)newFiller).processFile()){
							fillerTasks.addElement(newFiller);
						}
						else Debugg.println("Filler " + i + " processFile = " + ((SampleOneTreeFromFile)newFiller).processFile());
					}
				}
			} 
		}
		
		return true;
	}
	/*................................................................................................*/
	private TreeVector fillBlock(Taxa taxa){ //TODO: fill in
		TreeVector filledVector = new TreeVector(taxa);
		int vectorTreeCount=0;
			for(int fillers = 0; fillers < fillerTasks.size(); fillers++){
				if(fillerTasks.get(fillers) instanceof SampleOneTreeFromFile){
					((SampleOneTreeFromFile)fillerTasks.get(fillers)).resetTreesToSample(); //One unnecessary resetting of bits on first call
					TreeVector tempTreeVector = new TreeVector(taxa);
					((TreeBlockFiller)fillerTasks.get(fillers)).fillTreeBlock(tempTreeVector, 1);
					if(tempTreeVector.getNumberOfTrees() < 1){
						logln("Not enough trees were supplied by " + ((TreeBlockFiller)fillerTasks.get(fillers)).getName() + "; " + tempTreeVector.getNumberOfTrees() + " provided, one requested.");;
					}
					if(tempTreeVector.getNumberOfTrees() > 0){
						int treeCount = 0;
						while(treeCount < 1 && treeCount < tempTreeVector.getNumberOfTrees()){ //Check so we don't ask for more trees that are there.
							filledVector.addElement(tempTreeVector.getTree(treeCount), false);
							if(filledVector!= null && tempTreeVector!=null){
								if(filledVector.getTree(vectorTreeCount)!=null && tempTreeVector.getName()!=null){
									if(filledVector.getTree(vectorTreeCount).getName()!=null)
										logln(filledVector.getTree(vectorTreeCount).getName() + " added from " + tempTreeVector.getName());
								}
							}
							treeCount++;
							vectorTreeCount++;
						}
					}
					tempTreeVector.dispose(); //disposed to reduce memory demands

				//				(fillerTask.get(fillers)).
				}
			}
		return filledVector;
	}
	/*=====  For TreeBlockSource =====*/
	/*The getBlock method may be called multiple times by employer (or employer's employer, etc.), so the
	 * conditional checks to see if a new block is needed (i.e., if a new tree block index is
	 * indicated by the passed integer 'ic'.  For example, the calculateNumber method of 
	 * DeepCoalMultLoci will call this getBlock method every time DeepCoalMultLoci's employer
	 * calls DeepCoalMultLoci.calculateNumber method to evaluate a particular containing tree.
	 * During a tree search, this calculateNumber method is called multiple times, but to evaluate
	 * different containing trees; in this case, the block corresponding to the contained trees 
	 * (i.e. the block being returned this getBlock method) should NOT draw a new sample of trees,
	 * so the getCurrentBlock method is called.  Copied from SampleTreesMultSources*/
	/*................................................................................................*/
	public TreeVector getBlock(Taxa taxa, int ic) { //Copied from SampleTreesMultSources
 		setPreferredTaxa(taxa);
  		if(currentTreeBlockIndex != ic){
  			currentTreeBlockIndex=ic;
  			return getNextBlock(taxa);
  		}
  		else{
  			return getCurrentBlock(taxa);
  		}
	}
	/*................................................................................................*/
	public TreeVector getFirstBlock(Taxa taxa) { //Copied from SampleTreesMultSources
  		setPreferredTaxa(taxa);
   		currentTreeBlockIndex=0;
   		return getCurrentBlock(taxa);
	}
	/*................................................................................................*/
	public TreeVector getNextBlock(Taxa taxa) { //Copied from SampleTreesMultSources
   		setPreferredTaxa(taxa);
   		currentTreeBlock = fillBlock(taxa);
   		return currentTreeBlock;
	}
	/*................................................................................................*/
	public TreeVector getCurrentBlock(Taxa taxa) { //Copied from SampleTreesMultSources
		return currentTreeBlock;
	}
	/*................................................................................................*/
	public int getNumberOfTreeBlocks(Taxa taxa) { //Copied from SampleTreesMultSources
   		setPreferredTaxa(taxa);
   		return MesquiteInteger.infinite;
	}
	/*................................................................................................*/
	public String getTreeBlockNameString(Taxa taxa, int i) { //Copied from SampleTreesMultSources
   		setPreferredTaxa(taxa);
		return "Tree source tree block " + i;
	}
	/*=====  End For TreeBlockSource =====*/
	
	/*.................................................................................................................*/
	public boolean isSubstantive(){
		return false;
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
	/*.................................................................................................................*/
	/** returns whether this module is requesting to appear as a primary choice */
	public boolean requestPrimaryChoice(){
		return false;
	}
	/*.................................................................................................................*/
	public String getVersion(){
		return "1.0";
	}
}