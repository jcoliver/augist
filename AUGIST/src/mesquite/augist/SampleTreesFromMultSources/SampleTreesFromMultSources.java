package mesquite.augist.SampleTreesFromMultSources;

/* Mesquite source code.  Copyright 1997-2007 W. Maddison and D. Maddison. Module by J.C. Oliver.
Version 2.0, September 2007.
Disclaimer:  The Mesquite source code is lengthy and we are few.  There are no doubt inefficiencies and goofs in this code. 
The commenting leaves much to be desired. Please approach this source code with the spirit of helping out.
Perhaps with your help we can be more than a few, and make Mesquite better.

Mesquite is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY.
Mesquite's web site is http://mesquiteproject.org

This source code and its compiled class files are free and modifiable under the terms of 
GNU Lesser General Public License.  (http://www.gnu.org/copyleft/lesser.html)
 */

import java.util.Random;

import mesquite.lib.*;
import mesquite.lib.duties.*;
import mesquite.trees.*;
import mesquite.trees.SampleManyTreesFromFile.SampleManyTreesFromFile;
import mesquite.augist.SampleManyTreesCmd.*;

/**Supplies trees randomly selected from a user-defined TreeBlockFillers.*/
public class SampleTreesFromMultSources extends TreeBlockSource{
	TreeBlockFiller[] fillerTasks;
	int numSources = 1;
	int numPerSource = 1;
	Taxa currentTaxa = null;
	Taxa preferredTaxa = null;
	TreeVector currentTreeBlock = null;
	TreeVector lastUsedTreeBlock = null;
	int currentTreeBlockIndex = -1;
	MesquiteInteger pos = new MesquiteInteger(0); //For doCommand navigation

	public void getEmployeeNeeds(){  //This gets called on startup to harvest information; override this and inside, call registerEmployeeNeed
		EmployeeNeed e1 = registerEmployeeNeed(TreeBlockFiller.class, getName() + " needs a source of trees.", "The source of trees is indicated initially.");
	}
	
	public String getName() {
		return "Sample Trees from Multiple Sources";
	}
	public String getExplanation(){
		return "Supplies trees randomly selected from user-defined tree sources.";
	}
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		if (!MesquiteThread.isScripting()){
			int n = MesquiteInteger.queryInteger(containerOfModule(), "Number of Tree Sources?", "How many sources of trees to sample trees from?", numSources);
			if (!MesquiteInteger.isCombinable(n) || n<=0)
				return false;
			numSources = n;
			fillerTasks = new TreeBlockFiller[numSources];
		}
		boolean checkHire = false;
		if(!MesquiteThread.isScripting()){
			int n = MesquiteInteger.queryInteger(containerOfModule(), "Number of trees per source?", "How many random trees to sample per source", numPerSource);
			if(!MesquiteInteger.isCombinable(n) || n <=0)
				return false;
			numPerSource=n;
		}
		if(!MesquiteThread.isScripting()){//enclosed in conditional to avoid hiring query when opening file; should be handled by snapshot/doCommand when file is opened.
			for (int hireCount = 0; hireCount < numSources; hireCount++){
				checkHire = hireSources(hireCount, arguments);
				if (!checkHire){
					logln(getName() + " failed to hire Tree Source " + hireCount);
					return sorry(getName() + " could not start due to a problem requesing trees.  See log for details.");
				}
			}
		}
		return true;
	}
	/*.................................................................................................................*/
	/**Hires multiple Tree Block Fillers; called by startJob method.*/
	private boolean hireSources(int sourceNumber, String arguments){
		if(arguments!=null){
			fillerTasks[sourceNumber] = (TreeBlockFiller)hireNamedEmployee(TreeBlockFiller.class, arguments);
			if(fillerTasks[sourceNumber]==null)
				return sorry(getName() + " couldn't start because the requesting source of trees module was not obtained.");
			else if(fillerTasks[sourceNumber] instanceof SampleManyTreesCmd){
				((SampleManyTreesCmd)fillerTasks[sourceNumber]).setNumTreesToSample(numPerSource);
			}
		}
		else {
			fillerTasks[sourceNumber] = (TreeBlockFiller)hireEmployee(TreeBlockFiller.class, "Tree Block Source (" + (sourceNumber+1) + " of " + numSources + ")");
			if(fillerTasks[sourceNumber]==null)
				return sorry(getName() + " couldn't start because the requesting source of trees module was not obtained.");
			else if(fillerTasks[sourceNumber] instanceof SampleManyTreesCmd){
				((SampleManyTreesCmd)fillerTasks[sourceNumber]).setNumTreesToSample(numPerSource);
			}
		}
		return true;
	}
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		if(checker.compare(this.getClass(), "Sets the number of tree sources", "[number]", commandName, "setNumSources")){
			int tempNumSources = MesquiteInteger.fromString(arguments);
			if(MesquiteInteger.isCombinable(tempNumSources)){
				numSources = tempNumSources;
			}
		}
		else if(checker.compare(this.getClass(), "Sets the number of trees to sample from each source", "[number]", commandName, "setNumPerSource")){
			int tempPerSource = MesquiteInteger.fromString(arguments);
			if(MesquiteInteger.isCombinable(tempPerSource)){
				numPerSource = tempPerSource;
			}
		}
		else if(checker.compareStart(this.getClass(), "Sets module to fill tree block", "[name of module]", commandName, "hireSource")){
			String disposable = ParseUtil.getFirstToken(commandName, pos);  //A string used only to move the parser position.
			/*numSources MUST be set before this hiring command occurs, else the fillerTasks array has issues...*/
			int tempSourceNum = MesquiteInteger.fromString((ParseUtil.getToken(commandName, pos)));
			if(fillerTasks==null){
				fillerTasks = new TreeBlockFiller[numSources];
			}
			TreeBlockFiller tempFiller = (TreeBlockFiller)replaceEmployee(TreeBlockFiller.class, arguments, "Tree Block Filler", fillerTasks[tempSourceNum]);
			if(tempFiller!=null){
				fillerTasks[tempSourceNum] = tempFiller;
				return fillerTasks[tempSourceNum];
			}
		}
		return super.doCommand(commandName, arguments, checker);
	}
	
/*.................................................................................................................*/
	/**Fills a block of trees from the multiple sources defined by user*/
	private TreeVector fillBlock(Taxa taxa){
		TreeVector filledVector = new TreeVector(taxa);
		int vectorTreeCount=0;
		Random rng = new Random(System.currentTimeMillis());
		for(int sourceCount = 0; sourceCount < numSources; sourceCount++){
			/*First part of conditional avoids nested randomization; SampleManyTreesCmd takes care of the random sample, so this 
			 * part of the module does not.  If use does not choose SampleManyTreesCmd, the else part of the conditional is used (and
			 * the randomization is covered by this module).*/
			if(fillerTasks[sourceCount] instanceof SampleManyTreesCmd){
				((SampleManyTreesCmd)fillerTasks[sourceCount]).resetTreesToSample(); //One unnecessary resetting of bits on first call
				TreeVector tempTreeVector = new TreeVector(taxa);
				fillerTasks[sourceCount].fillTreeBlock(tempTreeVector, numPerSource);
				if(tempTreeVector.getNumberOfTrees() < numPerSource){
					logln("Not enough trees were supplied by " + fillerTasks[sourceCount].getName() + "; " + tempTreeVector.getNumberOfTrees() + " provided, " + numPerSource + " requested.");;
				}
				if(tempTreeVector.getNumberOfTrees() > 0){
					int treeCount = 0;
					while(treeCount < numPerSource && treeCount < tempTreeVector.getNumberOfTrees()){ //Check so we don't ask for more trees that are there.
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
			}
			else {
			TreeVector tempTreeVector = new TreeVector(taxa);
			fillerTasks[sourceCount].fillTreeBlock(tempTreeVector, numPerSource); //TBF
			if(tempTreeVector.getNumberOfTrees() > 0){
				int iRandomTree = rng.nextInt(tempTreeVector.getNumberOfTrees());
				for (int it = 0; it<numPerSource; it++){
					if(tempTreeVector.getNumberOfTrees() == 1){
						filledVector.addElement(tempTreeVector.getTree(0), false);
						vectorTreeCount++;
					}
					else{
						filledVector.addElement(tempTreeVector.getTree(iRandomTree), false);
						if(filledVector!= null && tempTreeVector!=null){
							if(filledVector.getTree(vectorTreeCount)!=null && tempTreeVector.getName()!=null){
							}
						}
						vectorTreeCount++;
					}
					iRandomTree = rng.nextInt(tempTreeVector.getNumberOfTrees());
				}
			}
			tempTreeVector.dispose(); //disposed to reduce memory demands
			}
		}
		return filledVector;
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file) {
		Snapshot temp = new Snapshot();
		temp.addLine("setNumSources " + numSources);
		temp.addLine("setNumPerSource " + numPerSource);
		if(MesquiteInteger.isCombinable(numSources)){
			for(int is = 0; is < numSources; is++){
				temp.addLine("hireSource_" + is + " ", fillerTasks[is]);
			}
		}
		return temp;
	}	
//TODO: WRITE SNAPSHOT METHOD
	
	/*.................................................................................................................*/
	/*=====  For TreeBlockSource =====*/
	/*The getBlock method may be called multiple times by employer (or employer's employer, etc.), so the
	 * conditional checks to see if a new block is needed (i.e., if a new tree block index is
	 * indicated by the passed integer 'ic'.  For example, the calculateNumber method of 
	 * DeepCoalMultLoci will call this getBlock method every time DeepCoalMultLoci's employer
	 * calls DeepCoalMultLoci.calculateNumber method to evaluate a particular containing tree.
	 * During a tree search, this calculateNumber method is called multiple times, but to evaluate
	 * different containing trees; in this case, the block corresponding to the contained trees 
	 * (i.e. the block being returned this getBlock method) should NOT draw a new sample of trees,
	 * so the getCurrentBlock method is called.*/
	public TreeVector getBlock(Taxa taxa, int ic) {
  		setPreferredTaxa(taxa);
  		if(currentTreeBlockIndex != ic){
  			currentTreeBlockIndex=ic;
  			return getNextBlock(taxa);
  		}
  		else{
  			return getCurrentBlock(taxa);
  		}
	}
	/*.................................................................................................................*/
	public TreeVector getCurrentBlock(Taxa taxa) {
		return currentTreeBlock;
	}
	/*.................................................................................................................*/
	public TreeVector getFirstBlock(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		currentTreeBlockIndex=0;
   		return getCurrentBlock(taxa);
	}
	/*.................................................................................................................*/
	public TreeVector getNextBlock(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		currentTreeBlock = fillBlock(taxa);
   		return currentTreeBlock;
	}
	/*.................................................................................................................*/
	public int getNumberOfTreeBlocks(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		return MesquiteInteger.infinite;
	}
	/*.................................................................................................................*/
	public String getTreeBlockNameString(Taxa taxa, int i) {
   		setPreferredTaxa(taxa);
		return "Tree source tree block " + i;
	}
	/*.................................................................................................................*/
	public void initialize(Taxa taxa) {
		setPreferredTaxa(taxa);
		for (int sourceCount = 0; sourceCount<numSources; sourceCount++){
			if(fillerTasks[sourceCount]!=null)
				fillerTasks[sourceCount].initialize(taxa);
		}
	}
	/*.................................................................................................................*/
	public void setPreferredTaxa(Taxa taxa) {
		if (taxa !=currentTaxa) {
			if (currentTaxa!=null)
				currentTaxa.removeListener(this);
			currentTaxa = taxa;
			currentTaxa.addListener(this);
		}
	}
	/*=====  End For TreeBlockSource =====*/

	/*.................................................................................................................*/
  	 public boolean isSubstantive(){
  	 	return false;
  	 }
	/*.................................................................................................................*/
  	 public boolean isPrerelease(){
  	 	return false;
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
