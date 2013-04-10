package mesquite.augist.ModalDiscordances;
/*Mesquite source code copyright 2011 Jeffrey C. Oliver
 * July.21.2011*/

import java.util.Vector;

import mesquite.assoc.lib.*;
import mesquite.lib.*;
import mesquite.lib.duties.*;

public class ModalDiscordances extends NumberForTree {
	TreeBlockSource treeSourceTask;
	AssociationSource associationTask;
	ReconstructAssociation reconstructTask;
	MesquiteString treeSourceName;
	TaxaAssociation association;
	int currentContainedTreeBlock = MesquiteInteger.unassigned;
	MesquiteCommand tstC;
	Tree lastTree;
	TreeVector geneTreeVector;
	MesquiteTree cloneGeneTree;
	Taxa containedTaxa;
	MesquiteDouble ageThreshold;
	AssociationHistory[] histories;
	
	//TODO: add getParameters method.
	//TODO: add getExplanation method.
	
	public boolean startJob(String arguments, Object condition,	boolean hiredByName) {
		associationTask = (AssociationSource)hireEmployee(AssociationSource.class, "Source of taxon associations");
		if (associationTask == null)
			return sorry(getName() + " couldn't start because no source of taxon associations obtained.");
		reconstructTask = (ReconstructAssociation)hireEmployee(ReconstructAssociation.class, "Method to reconstruct association history");
		if (reconstructTask == null)
			return sorry(getName() + " couldn't start because no association reconstructor module obtained.");
		treeSourceTask = (TreeBlockSource)hireEmployee(TreeBlockSource.class, "Source of contained trees");
		if (treeSourceTask == null)
			return sorry(getName() + " couldn't start because no source of trees obtained");
		tstC =  makeCommand("chooseTreeSource",  this);
		treeSourceTask.setHiringCommand(tstC);
		treeSourceName = new MesquiteString(treeSourceTask.getName());
		if (numModulesAvailable(TreeBlockSource.class)>1) {
			MesquiteSubmenuSpec mss = addSubmenu(null, "Gene Tree Source", tstC, TreeBlockSource.class);
			mss.setSelected(treeSourceName);
		}
		addMenuItem("Next Contained Tree block", makeCommand("nextContained",  this));
		addMenuItem("Previous Contained Tree block", makeCommand("previousContained",  this));
		
		ageThreshold = new MesquiteDouble(MesquiteDouble.unassigned);
		addMenuItem("Set Age Threshold...", makeCommand("setAgeThreshold", this));
		
		return true;
	}
	/*.................................................................................................................*/
	public Snapshot getSnapshot(MesquiteFile file){
		Snapshot temp = new Snapshot();
		temp.addLine("setAgeThreshold " + ageThreshold.getValue());
		temp.addLine("setTreeSource ", treeSourceTask);
		temp.addLine("setContained " + MesquiteTree.toExternal(currentContainedTreeBlock));
		return temp;
	}
	/*.................................................................................................................*/
	public Object doCommand(String commandName, String arguments, CommandChecker checker) {
		if (checker.compare(this.getClass(), "Sets the source of the gene tree", "[name of module]", commandName, "setTreeSource")) {
			TreeBlockSource temp = (TreeBlockSource)replaceEmployee(TreeBlockSource.class, arguments, "Source of trees", treeSourceTask);
			if (temp !=null){
				treeSourceTask = temp;
				treeSourceTask.setHiringCommand(tstC);
				treeSourceName.setValue(treeSourceTask.getName());
//				parametersChanged();
				return treeSourceTask;
			}
		}
		else if(checker.compare(this.getClass(), "Chooses the source of the gene tree(s)", "[name of module]", commandName, "chooseTreeSource")){
			TreeBlockSource temp = (TreeBlockSource)replaceEmployee(TreeBlockSource.class, arguments, "Source of trees", treeSourceTask);
			if (temp !=null){
				treeSourceTask = temp;
				treeSourceTask.setHiringCommand(tstC);
				treeSourceName.setValue(treeSourceTask.getName());
				parametersChanged();
				return treeSourceTask;
			}
		}
		else if (checker.compare(this.getClass(), "Goes to next block of contained gene trees", null, commandName, "nextContained")) {
			if (MesquiteInteger.isUnassigned(currentContainedTreeBlock))
				currentContainedTreeBlock = -1;
			setContained(currentContainedTreeBlock+1);
		}
		else if (checker.compare(this.getClass(), "Goes to previous block of contained gene trees", null, commandName, "previousContained")) {
			if (MesquiteInteger.isUnassigned(currentContainedTreeBlock))
				currentContainedTreeBlock = 1;
			setContained(currentContainedTreeBlock-1);
		}
		else if (checker.compare(this.getClass(), "Goes to block of contained gene trees", "[number of tree]", commandName, "setContained")) {
			int ic = MesquiteTree.toInternal(MesquiteInteger.fromFirstToken(arguments, stringPos)); 
			setContained(ic);
		}
		else if (checker.compare(this.getClass(), "Sets the age threshold of nodes to consider", "[threshold]", commandName, "setAgeThreshold")){
			stringPos.setValue(0);
			double aT = MesquiteDouble.fromString(arguments, stringPos);
			if(!MesquiteDouble.isCombinable(aT)){
				aT = MesquiteDouble.queryDouble(containerOfModule(), "Age Threshold", "Depth of nodes to be considered", "The age threshold determines the cutoff for which nodes are to be considered; the value reflects node depth relative to the root node.  A value of zero (0) will consider all nodes, a value of 0.5 considers only those nodes in the older half of the tree.  A value of 1 includes only those nodes older than the root (i.e. no nodes), so it wouldn't be very useful to enter 1...", ageThreshold.getValue(), 0, 1);
			}
			if(!MesquiteDouble.isCombinable(aT)){
				return null;
			}
			if(ageThreshold.getValue() != aT){
				ageThreshold.setValue(aT);
				parametersChanged();
			}
		}
		else return  super.doCommand(commandName, arguments, checker);
		return null;
	}
	/*.................................................................................................................*/
	public void setContained (int index){ 
		if (containedTaxa == null)
			return;
		if (index<((TreeBlockSource)treeSourceTask).getNumberOfTreeBlocks(containedTaxa) && index>=0){
			currentContainedTreeBlock=index;
			parametersChanged();
		}
	}

	/*.................................................................................................................*/
	public void calculateNumber(Tree speciesTree, MesquiteNumber result, MesquiteString resultString) {
		if (result==null || speciesTree == null)
			return;
		clearResultAndLastResult(result);
		lastTree = speciesTree;
		Taxa taxa = speciesTree.getTaxa();

		//getting association & contained taxa
		if (association == null || (association.getTaxa(0)!= taxa && association.getTaxa(1)!= taxa)) {
			association = associationTask.getCurrentAssociation(taxa); 
			if (association == null)
				association = associationTask.getAssociation(taxa, 0); 
			if (association == null){
				if (resultString!=null)
					resultString.setValue("Modal Discordances not calculated (no association )");
				return;
			}
			if (association.getTaxa(0)== taxa)
				containedTaxa = association.getTaxa(1);
			else
				containedTaxa = association.getTaxa(0);
		}
		/* need to find out for each taxon among contained taxa if it has more than one associate.  
		 * If so, then can't do calculations since gene copy in more than one species*/
		for (int i=0; i< containedTaxa.getNumTaxa(); i++){
			Taxon tax = containedTaxa.getTaxon(i);
			if (association.getNumAssociates(tax)>1){
				if (resultString!=null)
					resultString.setValue("Modal Discordances not calculated (some genes in more than one species)");
				return;
			}
		}

		//If not already assigned, query the user for age threshold value
		if(ageThreshold.getValue() == MesquiteDouble.unassigned){
			String ageHelp = "The age threshold determines the cutoff for which nodes are to be considered; the value reflects node depth relative to the root node.  A value of zero (0) will consider all nodes, a value of 0.5 considers only those nodes in the older half of the tree.  A value of 1 includes only those nodes older than the root (i.e. zero nodes), so it wouldn't be very useful to enter a value of 1...";
			ageThreshold = new MesquiteDouble(0.5);
			double aT = MesquiteDouble.queryDouble(containerOfModule(), "Age Threshold", "Depth of nodes to be considered", ageHelp, ageThreshold.getValue(), 0, 1);
			if(MesquiteDouble.isCombinable(aT) && aT <= 1.0 && aT >= 0.0){
				ageThreshold.setValue(aT);
			} 
			else {
				if(resultString != null){
					resultString.setValue("Modal Discordance not calculated because age threshold (" + MesquiteDouble.toFixedWidthString(aT, 4) + ")is not within acceptable range (0-1).");
				}
				return;
			}
		}

		//choosing which tree block from the tree block source
		if (MesquiteInteger.isUnassigned(currentContainedTreeBlock)){
			if (MesquiteThread.isScripting())
				currentContainedTreeBlock = 0;
			else {
				int nt = ((TreeBlockSource)treeSourceTask).getNumberOfTreeBlocks(containedTaxa);
				if (nt>1 && !MesquiteThread.isScripting()) {
					currentContainedTreeBlock = ((TreeBlockSource)treeSourceTask).queryUserChoose(containedTaxa, "Which trees to serve as source of gene trees to fit into species tree to count deep coalescences?");
					if (MesquiteInteger.isUnassigned(currentContainedTreeBlock))
						currentContainedTreeBlock = 0;
				}
				else
					currentContainedTreeBlock = 0;
			}
		}

		//getting the contained trees
		geneTreeVector = ((TreeBlockSource)treeSourceTask).getBlock(containedTaxa, currentContainedTreeBlock);

		double depthThreshold = speciesTree.tallestPathAboveNode(speciesTree.getRoot()) * ageThreshold.getValue();

		if (geneTreeVector==null) {
			if (resultString!=null)
				resultString.setValue("Modal Discordances: unassigned (no gene trees)");
			return;
		}
		result.setValue(0);

		histories = new AssociationHistory[geneTreeVector.getNumberOfTrees()];

		visitNodes(speciesTree, speciesTree.getRoot(), depthThreshold, result);


		if (resultString!=null){
			resultString.setValue(result.toString() + " modal discordant nodes (" + geneTreeVector.getNumberOfTrees() + " gene trees)");
		}
		saveLastResult(result);
		saveLastResultString(resultString);
	}
	/*.................................................................................................................*/
	/**Compares nodes of two trees to see if they have (exclusively) the same terminal taxa.*/
	private boolean nodesAreSame(Tree treeOne, int nodeOne, Tree treeTwo, int nodeTwo){
		Bits cladeOne = treeOne.getTerminalTaxaAsBits(nodeOne);
		Bits cladeTwo = treeTwo.getTerminalTaxaAsBits(nodeTwo);

		if(!cladeOne.equals(cladeTwo)){
			return false;
		}
		return true;
		
	}
	/*.................................................................................................................*/
	/**Compares nodes of two trees to see if they attach at same points of their respective gene trees.*/
	private boolean nodesAttachSame(Tree treeOne, int nodeOne, Tree treeTwo, int nodeTwo){
		//This was previously contained within nodesAreSame, but moved to allow earlier exit from checks within historiesMatch.
		Bits parentCladeOne = treeOne.getTerminalTaxaAsBits(treeOne.motherOfNode(nodeOne));
		Bits parentCladeTwo = treeTwo.getTerminalTaxaAsBits(treeTwo.motherOfNode(nodeTwo));

		if(!parentCladeOne.equals(parentCladeTwo)){
			return false;
		}
		return true;
	}
	
	/*.................................................................................................................*/
	/**Compares histories for a particular node in species tree, as stored in NodeHistory objects.  
	 * Requires both (A) coalescent histories to be identical and (B) topologies (of only those 
	 * contained nodes) to be identical.  For part A, the contained nodes must also
	 * coalesce at the same place in the gene tree, even though the coalesence may occur in a 
	 * species tree node ancestral to the current node.*/
	private boolean historiesMatch(NodeHistory queryHistory, NodeHistory uniqueHistory){
		//If there are different numbers of contained nodes, the histories don't match.
		if(queryHistory.getNumContainedNodes() != uniqueHistory.getNumContainedNodes()){
			return false;
		}
		int queryNodeIndex = 0;//A counter, not a node number!
		//For comparing clades in the two histories.
		boolean cladeMatch[] = new boolean[queryHistory.getNumContainedNodes()];
		for(int cM = 0; cM < cladeMatch.length; cM++){
			cladeMatch[cM] = false;
		}
		//Looping over all contained nodes in the queryHistory, as long as gene trees have the same clades (as long as node exists).
		while(queryNodeIndex < queryHistory.getNumContainedNodes() && (queryHistory.getClonedGeneTree()).nodeExists(queryHistory.getNodeNumber(queryNodeIndex))){
			boolean foundMatch = false;
			int uniqueNodeIndex = 0;//A counter, not a node number!
			//Looping over all contained nodes in the uniqueHistory, in attempt to find same clade in queryHistory's contained node, queryNode (as long as node exists).
			while(uniqueNodeIndex < uniqueHistory.getNumContainedNodes() && !foundMatch && (uniqueHistory.getClonedGeneTree()).nodeExists(uniqueHistory.getNodeNumber(uniqueNodeIndex))){
				//Check if these nodes in the two gene trees coalesce at the same point in the species tree; Coalescence Check.
				if(queryHistory.getCoalescentPoint(queryNodeIndex) == uniqueHistory.getCoalescentPoint(uniqueNodeIndex)){
					//Check if nodes (i) represent same clades and (ii) coalesce at same point in respective gene trees; Topology Check.
					if(nodesAreSame(queryHistory.getClonedGeneTree(), queryHistory.getNodeNumber(queryNodeIndex), uniqueHistory.getClonedGeneTree(), uniqueHistory.getNodeNumber(uniqueNodeIndex))){
						if(nodesAttachSame(queryHistory.getClonedGeneTree(), queryHistory.getNodeNumber(queryNodeIndex), uniqueHistory.getClonedGeneTree(), uniqueHistory.getNodeNumber(uniqueNodeIndex))){
							cladeMatch[queryNodeIndex] = true;
							foundMatch = true;
						} else return false; //Added for early exit if nodes are the same, but attach at different points of their respective gene trees
					}
				}
				uniqueNodeIndex++;
			}
			queryNodeIndex++;
		}
		for(int cM = 0; cM < cladeMatch.length; cM++){
			if(!cladeMatch[cM])
				return false;
		}
		return true;
	}
	/*.................................................................................................................*/
	/**Recurses through species tree, checking all gene (contained) trees at that species (containing) node.*/
	private void visitNodes(Tree speciesTree, int spTreeNode, double depthThreshold, MesquiteNumber result){

		if(speciesTree.nodeExists(spTreeNode)){
			if(speciesTree.tallestPathAboveNode(spTreeNode) >= depthThreshold){
				for(int d = speciesTree.firstDaughterOfNode(spTreeNode); speciesTree.nodeExists(d); d = speciesTree.nextSisterOfNode(d)){
					if(speciesTree.tallestPathAboveNode(d) >= depthThreshold){
						visitNodes(speciesTree, d, depthThreshold, result);
					}
				}
			}
			//only check internal nodes, nodes that are older than the age threshold, and skip the root;
			if(speciesTree.nodeIsInternal(spTreeNode) && speciesTree.tallestPathAboveNode(spTreeNode) >= depthThreshold  && spTreeNode != speciesTree.getRoot()){

				//Will hold each unique discordant history, for comparisons
				Vector uniqueDiscordantHistories = new Vector();
				boolean checkSpeciesNode = true;
				int concordantCount = 0;
				MesquiteNumber sumForHistory = new MesquiteNumber(0);//for reconstructing gene tree history
				MesquiteString resultString = new MesquiteString("");//for reconstructing gene tree history
				int geneTreeCount = 0;
				/*Look at all gene trees, unless (or until) over half are concordant with species tree.  If of half are concordant, 
				 * the modal gene tree is condordant with the species tree.*/
				while(geneTreeCount < geneTreeVector.getNumberOfTrees() && concordantCount < (geneTreeVector.getNumberOfTrees()/2)){

					checkSpeciesNode = true;
					MesquiteTree clonedGeneTree = (geneTreeVector.getTree(geneTreeCount)).cloneTree();

					/*If history for particular gene has not been reconstructed, do so now.  Allows history for a particular gene to be
					 * reconstructed once, as opposed to multiple times, once for each node.*/
					if(histories[geneTreeCount] == null){
						histories[geneTreeCount] = reconstructTask.reconstructHistory(speciesTree, clonedGeneTree, association, sumForHistory, resultString);
					}

					int containedNodes[] = histories[geneTreeCount].getContainedNodes(spTreeNode);
					int containedCount = 0;
					boolean anyContainedDiscordant = false;//Will be used for determining number of concordant gene trees.

					/*The array containedNodes appears to be filled starting at the first element [0].  There should not be any combinable
					 * values past the first element in the array which has a non-combinable value.*/
					while(checkSpeciesNode && MesquiteInteger.isCombinable(containedNodes[containedCount])){

						//Checks to see if contained node is also present in mother of the current species tree node - if so, it is discordant.
						if(histories[geneTreeCount].isNodeContained(speciesTree.motherOfNode(spTreeNode), containedNodes[containedCount])){
							anyContainedDiscordant = true;
							checkSpeciesNode = false;
							int numberCombinable = 0;
							/*Loop to count which nodes in containedNodes array are actually in tree.  
							 * The array containedNodes has as many elements as there are nodes (internal 
							 * & external) in the tree, so for any node of the gene tree which is not 
							 * contained by current species node, the array holds a non-combinable number 
							 * (MesquiteInteger.unassigned).  No need to fill an array with all those 
							 * numbers, so count the number that are combinable, and make an array 
							 * (coalPoints) which has appropriate length.  Another way would be to check 
							 * to see if the value in the containedNodes array represents a node in the
							 * gene tree (e.g. clonedGeneTree.nodeInTree(containedNodes[i]), but that 
							 * approach does a full recursion of the tree, potentially adding considerable
							 * computational time.*/

							//A bit inelegant, I know.
							while(numberCombinable < containedNodes.length && MesquiteInteger.isCombinable(containedNodes[numberCombinable])){
								numberCombinable++;
							}
							int coalPoints[][] = new int [numberCombinable][2];
							for(int cN = 0; cN < numberCombinable; cN++){
								if(MesquiteInteger.isCombinable(containedNodes[cN])){
									coalPoints[cN][0] = containedNodes[cN];
									coalPoints[cN][1] = histories[geneTreeCount].deepestNodeThatContainedEnters(containedNodes[cN]);
								}
							}
							NodeHistory testHistory = new NodeHistory(clonedGeneTree, coalPoints);

							//If there are already unique histories, compare this new one to those.
							if(uniqueDiscordantHistories.size() > 0){
								boolean foundIdentical = false;
								int historyCount = 0;

								//only compare until an identical history is found, or all previously identified unique discordant histories have been checked.
								while(!foundIdentical && historyCount < uniqueDiscordantHistories.size()){
									//Check to see if histories match in topology and coalescence.
									if(historiesMatch(testHistory, (NodeHistory)uniqueDiscordantHistories.get(historyCount))){

										//Keeps track of number of histories matching this one, for later calculations.
										((NodeHistory)uniqueDiscordantHistories.get(historyCount)).incrementNumMatching();
										foundIdentical = true;
									}
									historyCount++;
								}
								//If the testHistory doesn't match any of the current uniqueDiscordantHistorys, add it to the vector.
								if(!foundIdentical){
									uniqueDiscordantHistories.addElement(testHistory);
								}
							} //First unique discordant history, so just add that as the first element to the vector.
							else {
								uniqueDiscordantHistories.removeAllElements();//shouldn't be necessary
								uniqueDiscordantHistories.addElement(testHistory);
							}
						}
						containedCount++;
					}
					if(!anyContainedDiscordant){
						concordantCount++;
					}
					geneTreeCount++;
				}
				/*Have looked at all gene trees (or found at least half gene trees to be concordant with species tree node);
				 * if less than half are concordant, see if any discordant histories occur more frequently than do
				 * concordant histories. */
				if((double)concordantCount < (double)geneTreeVector.getNumberOfTrees()/2){
					int uniques = 0;
					boolean incremented = false;
					while(uniques < uniqueDiscordantHistories.size() && !incremented){
						int numInHistory = ((NodeHistory)uniqueDiscordantHistories.get(uniques)).getNumMatching();
						if (numInHistory > (concordantCount)){
							incremented = true;
							result.add(1);
						}
						uniques++;
					}
				}
			}			

		}
	}
	/*.................................................................................................................*/
	public String getParameters(){
		return "Age threshold " + ageThreshold;
	}
	/*.................................................................................................................*/
	public String getName() {
		return "Modal Discordances";
	}
	/*.................................................................................................................*/
	public boolean isPrerelease(){
		return true;
	}
	/*.................................................................................................................*/
	public boolean isSubstantial(){
		return false;
	}
}

class NodeHistory {
	Tree geneTree;
	/*For matrix i & j: Each row corresponds to a unique node in the gene tree [i][0] and the 
	 * node in the species tree where it coalesces [i][1].*/
	int[][] coalescentPoints;
	int numMatching = 1;

	public NodeHistory(Tree geneTree, int[][] coalescentPoints){
		if(geneTree != null){
			this.geneTree = geneTree;
		}
		if(coalescentPoints != null){
			this.coalescentPoints = coalescentPoints;
		}
	}
	/*.................................................................................................................*/
	public int getNumContainedNodes(){
		return coalescentPoints.length;
	}
	/*.................................................................................................................*/
	public Tree getClonedGeneTree(){
		return geneTree.cloneTree();
	}
	/*.................................................................................................................*/
	public int[][] getCoalescentPoints(){
		return coalescentPoints;
	}
	/*.................................................................................................................*/
	public int getCoalescentPoint(int index){
		return coalescentPoints[index][1];
	}
	/*.................................................................................................................*/
	public int getNodeNumber(int index){
		return coalescentPoints[index][0];
	}
	/*.................................................................................................................*/
	public void incrementNumMatching(){
		numMatching++;
	}
	/*.................................................................................................................*/
	public int getNumMatching(){
		return numMatching;
	}
	/*.................................................................................................................*/
	/**Primarily designed for debugging purposes.  If includeBits==true, the terminal descendants represented by a string of
	 * bits is also displayed.*/
	public String toString(boolean includeBits){
		String s = "";
		for(int nodes = 0; nodes < coalescentPoints.length; nodes++){
			s += "Node " + coalescentPoints[nodes][0];
			if(includeBits){
				String b = (geneTree.getTerminalTaxaAsBits(coalescentPoints[nodes][0])).toString();
				s += " [" + b + "]";
			}
			s += " coalesces in species tree node " + coalescentPoints[nodes][1];
			if(includeBits){
				int parent = geneTree.motherOfNode(coalescentPoints[nodes][0]);
				String p = (geneTree.getTerminalTaxaAsBits(parent)).toString();
				s += " (parent : [" + p + "]";
			}
			s += "\n";
		}
		return s;
	}
}