package mesquite.augist.lib;

import mesquite.consensus.lib.*;
import mesquite.lib.*;
import mesquite.lib.duties.*;


/**Similar to BasicTreeConsenser, but subclasses are not designed for direct user interface [in contrast,
 * BasicTreeConsenser subclasses (e.g. MajRuleTree) are designed for user interaction].  Prevents the
 * subclasses from being listed when hiring a Tree consenser.*/
public abstract class CmdTreeConsenser extends IncrementalConsenser {

	protected static final int ASIS=0;
	protected static final int ROOTED =1;
	protected static final int UNROOTED=2;
	protected int rooting = ASIS;   // controls and preferences and snapshot should be in subclass

	protected BipartitionVector bipartitions=null;
	protected int treeNumber = 0;
	boolean preferencesSet = false;

	/*.................................................................................................................*/
	public boolean startJob(String arguments, Object condition, boolean hiredByName) {
		bipartitions = new BipartitionVector();
		loadPreferences();
//		if (!MesquiteThread.isScripting()) 
//			if (!queryOptions())
//				return false;
		return true;
	}
	/*.................................................................................................................*/
	/**Sets the rooting preference: 1 = rooted, 2 = unrooted, 0 (or any other value) = roots as is.  Functions as
	 * the queryOptions() method does in BasicTreeConsenser */
	public void setRooting(int root){
		if(MesquiteInteger.isCombinable(root)){
			rooting = root;
		} else rooting = ASIS;
	}
	/*.................................................................................................................*/
	public int getRooting(){
		return rooting;
	}
	/*.................................................................................................................*/
  	public void reset(Taxa taxa){
  		if (bipartitions==null)
  			bipartitions = new BipartitionVector();
  		else
  			bipartitions.removeAllElements();		// clean bipartition table
		bipartitions.setTaxa(taxa);
		bipartitions.zeroFrequencies();
		initialize();
	}
  	public abstract void addTree(Tree t);
 	public abstract Tree getConsensus();
	/*.................................................................................................................*/
 	public void initialize() {
 	}
	/*.................................................................................................................*/
	//ASSUMES TREES HAVE ALL THE SAME TAXA
	/*.................................................................................................................*/
	public Tree consense(Trees list){
		Taxa taxa = list.getTaxa();
		
		reset(taxa);
		MesquiteTimer timer = new MesquiteTimer();
		timer.start();
		logln("");
		for (treeNumber = 0; treeNumber < list.size(); treeNumber++){
			if (treeNumber==0) {
				switch (rooting) {
				case ASIS: 
					bipartitions.setRooted(list.getTree(0).getRooted());
					break;
				case ROOTED: 
					bipartitions.setRooted(true);
					break;
				case UNROOTED: 
					bipartitions.setRooted(false);
					break;
				}
			}
			addTree(list.getTree(treeNumber));
			if (treeNumber%100==0)
				log(".");
		}
		Tree t = getConsensus();
		double time = 1.0*timer.timeSinceLast()/1000.0;
		timer = null;
		
		logln("\n" + list.size() + " trees processed in " + time + " seconds");
		return t;
	}

	/*.................................................................................................................*/
	/*  ====  For use with/by XMLUtil  ====  */
	public void processMorePreferences (String tag, String content) {
	}
	/*.................................................................................................................*/
	public void processSingleXMLPreference (String tag, String content) {
		if ("rooting".equalsIgnoreCase(tag))
			rooting = MesquiteInteger.fromString(content);
		processMorePreferences(tag, content);
		preferencesSet = true;
	}
	/*.................................................................................................................*/
	public String prepareMorePreferencesForXML () {
		return "";
	}
	/*.................................................................................................................*/
	public String preparePreferencesForXML () {
		StringBuffer buffer = new StringBuffer(200);
		StringUtil.appendXMLTag(buffer, 2, "rooting", rooting);  
		buffer.append(prepareMorePreferencesForXML());
		preferencesSet = true;
		return buffer.toString();
	}

}