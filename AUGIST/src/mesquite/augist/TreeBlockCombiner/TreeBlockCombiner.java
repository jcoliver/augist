package mesquite.augist.TreeBlockCombiner;

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

import java.util.*;
import java.awt.*;
import mesquite.lib.*;
import mesquite.lib.duties.*;
//TODO: would be nice to turn off the TreeOptimizer notification, if possible
/**Hires another TreeBlockSource, and combines the multiple Tree Blocks the hired TreeBlockSource
 * into another, single Tree Block (but as with other TreeBlockSource objects, MultipleTreeBlockCombiner
 * can be called multiple times [the last user query determines how many times the MultipleTreeBlockCombiner] is
 * called).  MultipleTreeBlockCombiner should probably hire a TreeBlockFiller, instead of a TreeBlockSource (the 
 * object fillerTask is a TreeBlockSource); however, when the TreeBlockFiller is initialized, and the source of 
 * trees requires a secondary source of trees (e.g. a Tree Search tree source using DeepCoalescences of multiple 
 * loci as the search criterion and the source of trees for the tree search are simulated
 * Contained Coalescent trees) the same random seed is used for all the secondary trees, so each tree search is
 * based on the same set of coalescent gene trees.*/
public class TreeBlockCombiner extends TreeBlockSource {
	TreeBlockSource fillerTask;
	TreeVector currentTreeBlock = null;
	TreeVector lastUsedTreeBlock = null;
	int currentTreeBlockIndex = 0;
	static int numSearches = 10;
	Taxa currentTaxa = null;
	Taxa preferredTaxa = null;
	MesquiteBoolean useWeights = new MesquiteBoolean(false);
	public String getName(){
		return "Tree Block Combiner";
	}
	public String getExplanation() {
		return "Supplies trees from multiple tree blocks and saves them in a single block.  All tree blocks are of the same type; a different" +
				"module, Multiple Tree Source Combiner, offers the flexibility of saving trees from tree blocks of different types.";
	}
	public void getEmployeeNeeds(){  //This gets called on startup to harvest information
		EmployeeNeed e = registerEmployeeNeed(TreeBlockCombiner.class, getName() + " requires another source of trees to fill block.", "Supplies trees from multiple tree blocks and saves them in a single block");
	}
	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
    	currentTreeBlockIndex = 0;
    	if(arguments!=null){
    		fillerTask = (TreeBlockSource)hireNamedEmployee(TreeBlockSource.class, arguments);
    		if(fillerTask==null){
    			return sorry(getName()+ " couldn't start because the requesting source of trees module was not obtained.");
    		}
    	}
    	else {
        	fillerTask = (TreeBlockSource)hireEmployee(TreeBlockSource.class, "Tree Block Source");
        	if (fillerTask==null) return sorry(getName() + " couldn't start because tree source module not obtained.");
    	}
    	if(!MesquiteThread.isScripting()){
    		int n = 1;
    		String helpString = "<h3>" + this.getName() + "</h3>";
    		helpString = helpString + "\nTree weights may be desired when the source of trees is a Tree Search, and multiple tree searches are being performed (e.g. nonparametric bootstrapping).";
    		MesquiteInteger buttonPressed = new MesquiteInteger(1);
			ExtensibleDialog numberAndWeightDialog = new ExtensibleDialog(containerOfModule(), "Tree Block source options", buttonPressed);
    		numberAndWeightDialog.addLargeOrSmallTextLabel("Sets number of sources (blocks) of trees to include in file and whether to weight the trees based on block size.");
    		IntegerField numField = numberAndWeightDialog.addIntegerField("Number of tree sources from which to save trees:", n, 10);
			Checkbox useWeightBox = numberAndWeightDialog.addCheckBox("Store Tree Weights", useWeights.getValue());
			numberAndWeightDialog.appendToHelpString(helpString);
			numberAndWeightDialog.completeAndShowDialog(true);
			
			if (buttonPressed.getValue()==0) {
				useWeights.setValue(useWeightBox.getState());
				if(!MesquiteInteger.isCombinable(numField.getValue()) || n<=0)
					return false;
				numSearches=numField.getValue();
			}
			numberAndWeightDialog.dispose();
    	}
    	return true;
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file){
		Snapshot temp = new Snapshot();
		temp.addLine("getTreeBlock ", fillerTask);
		return temp;
	}
	
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {//TODO: necessary?
		if(checker.compare(this.getClass(), "Sets the module providing the trees", "[name of module]", commandName, "getTreeBlock")){
			TreeBlockSource temp = (TreeBlockSource)replaceEmployee(TreeBlockSource.class, arguments, "Tree Block Source", fillerTask);
			if(temp!=null){
				fillerTask = temp;
				parametersChanged(null); //?
			}
			return fillerTask;
		}
		else
			return super.doCommand(commandName, arguments, checker);
	}
	/*.................................................................................................................*/
	/** passes which object changed*/
	public void disposing(Object obj){ //TODO: necessary?
		if (obj == currentTaxa) {
			setHiringCommand(null); //since there is no rehiring
			iQuit();
		}
	}
	/*.................................................................................................................*/
	/**Assigns weights to trees based on the size of the TreeVector (tree block).  Each tree is assigned a weight of 1/T, where T = the number
	 * of trees in the TreeVector object.  For example, each tree in a TreeVector of 30 trees has a weight of 1/30.
	 * 
	 * Tree weights are attached to each tree as an attachment, and when written to a file, this
	 * attachment is used to determine if trees have weight, and if so, what the weight of each tree is.
	 * Relevant methods in TreeVector include setWriteWeights and getWriteWeights.  The actual writing of 
	 * weights is done by ManageTrees.*/
	private void assignWeights (TreeVector treeList){
		if (treeList!=null){
			treeList.setWriteWeights(true);
			int numInBlock = treeList.getNumberOfTrees();
			if(numInBlock>0){
				MesquiteDouble weightDouble = new MesquiteDouble();
				weightDouble.setName("Weight");
				double weight = (1.0/((double)numInBlock)) * 1.0;
				weightDouble.setValue(weight);
				for(int nTrees = 0; nTrees < numInBlock; nTrees++){
					MesquiteTree mTree = (MesquiteTree)treeList.getTree(nTrees);
					//mTree.attach(weightDouble);
					mTree.attachIfUniqueName(weightDouble);
				}
			}
		}
	}
	/**Fills and returns a TreeVector for a given set of taxa.  The TreeBlockSource fillerTask determines
	 * the source of the trees.*/
	private TreeVector fillBlock(Taxa taxa){
		TreeVector treeList = new TreeVector(taxa);
		TreeVector tempTreeVector = new TreeVector(taxa);

		ProgressIndicator progIndicator = new ProgressIndicator(getProject(), "Combining Tree Blocks" , "Combining Tree Blocks", numSearches, "Stop Filling");
		if (progIndicator!=null){
			progIndicator.setButtonMode(ProgressIndicator.OFFER_CONTINUE);
			progIndicator.setStopButtonName("Stop Filling");
			progIndicator.setOfferContinueMessageString("Are you sure you want to cancel?");
			progIndicator.setTertiaryMessage("Combining " + taxa.getName() + " trees.");
			progIndicator.start();
		}
		boolean keepFilling = true;
		int i = 0;
		while(keepFilling && i < numSearches){
			if (progIndicator != null) {
				if (progIndicator.isAborted()) {
					progIndicator.goAway();
					if(!MesquiteThread.isScripting()){
						alert(getName() + " cancelled by user.  Tree blocks not saved.");
					}
					else logln(getName() + " cancelled by user.  Tree blocks not saved.");
					keepFilling = false;
				}
				int current = i+1;
				progIndicator.setText("Tree block " + current);
				progIndicator.setSecondaryMessage("Filling block " + current + " of " + numSearches + " blocks.");
				progIndicator.setCurrentValue(i);
			}

			if(i == 0 && keepFilling){
				//fillerTask used to be a TreeBlockFiller, but there were random seed issues, so it is currently a TreeBlockFiller
				treeList = fillerTask.getBlock(taxa, i);
				int numInBlock = treeList.getNumberOfTrees();
				for (int nT = 0; nT < numInBlock; nT++){
					String nameAppend = ((i + 1) + " of " + numSearches + " " + fillerTask.getName());
					String origName = treeList.getTree(nT).getName();
					MesquiteString newTreeName = new MesquiteString(origName);
					newTreeName.append(" (" + nameAppend + ")");
					((MesquiteTree)treeList.getTree(nT)).setName(newTreeName.getValue());
				}
				if(useWeights.getValue()){
					treeList.setWriteWeights(true);
					assignWeights(treeList);
				}
			}
			else if (keepFilling){
				tempTreeVector = fillerTask.getBlock(taxa, i);
				int numTrees = tempTreeVector.getNumberOfTrees();
				for (int nT = 0; nT < numTrees; nT++){
					String nameAppend = ((i + 1) + " of " + numSearches + " " + fillerTask.getName());
					String origName = tempTreeVector.getTree(nT).getName();
					MesquiteString newTreeName = new MesquiteString(origName);
					newTreeName.append(" (" + nameAppend + ")");
					((MesquiteTree)tempTreeVector.getTree(nT)).setName(newTreeName.getValue());
				}
				if(useWeights.getValue()){
					tempTreeVector.setWriteWeights(true);
					assignWeights(tempTreeVector);
				}
				for(int iTreeToAppend = 0; iTreeToAppend < numTrees; iTreeToAppend++){
					treeList.addElement(tempTreeVector.getTree(iTreeToAppend), false);
					if(useWeights.getValue())
						treeList.setWriteWeights(true);
				}
			}
			i++;
		}
		treeList.setName("Trees from " + fillerTask.getName());
		if (progIndicator!=null) 
			progIndicator.goAway();
		return treeList;
	}
	
	/*=====  For TreeBlockSource =====*/
	public TreeVector getBlock(Taxa taxa, int ic) {
  		setPreferredTaxa(taxa);
   		currentTreeBlockIndex=ic;
   		return getCurrentBlock(taxa);
	}
	public TreeVector getCurrentBlock(Taxa taxa) {
		return fillBlock(taxa);
	}
	public TreeVector getFirstBlock(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		currentTreeBlockIndex=0;
   		return getCurrentBlock(taxa);
	}
	public TreeVector getNextBlock(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		currentTreeBlockIndex++;
   		return getCurrentBlock(taxa);
	}
	public int getNumberOfTreeBlocks(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		return MesquiteInteger.infinite;
	}
	public String getTreeBlockNameString(Taxa taxa, int i) {
   		setPreferredTaxa(taxa);
		return "Tree search tree block " + i;
	}
	public void initialize(Taxa taxa) {
   		setPreferredTaxa(taxa);
   		if (fillerTask!=null)
   			fillerTask.initialize(taxa);
	}
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
	/*.................................................................................................................*/
	public void endJob(){
		if (currentTaxa!=null)
			currentTaxa.removeListener(this);
		super.endJob();
	}
	/*.................................................................................................................*/
	/** passes which object changed*/
	public void changed(Object caller, Object obj, Notification notification){// TODO: necessary?
		if (Notification.appearsCosmetic(notification))
			return;
		int code = Notification.getCode(notification);
		if (obj == currentTaxa && !(code == MesquiteListener.SELECTION_CHANGED)) {
				parametersChanged(notification);
		}
	}
}

