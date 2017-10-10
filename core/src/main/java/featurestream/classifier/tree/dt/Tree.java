/* 
 * Copyright (C) 2013 Andrew Twigg - All Rights Reserved
 * Unauthorized copying or distribution 
 * of this file, via any medium, is strictly prohibited
 * Proprietary and confidential
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 */

package featurestream.classifier.tree.dt;

import featurestream.utils.Stats;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

/**
 * Convenience class for storing root pointer
 * 
 * @author adt
 *
 */
public class Tree implements Serializable {
	
	private static final long serialVersionUID = 1L;

	public Node root;
	public int max_id;
	public Map<Integer,Leaf> leafIdMap;
	
	public Tree() {
		max_id = 0;
		leafIdMap = new HashMap<Integer,Leaf>();
		this.root = new Leaf(null, this);
	}
	
	public Stats getStats() {
		Stats stats = new Stats();
		stats.add("type", "tree");
		stats.add("size", root.nbNodes());
		stats.add("depth", root.maxDepth());
		return stats;
	}

	public Leaf getLeaf(Integer i) {
		return leafIdMap.get(i);
	}
	
	public void putLeaf(Leaf l) {
		l.id = max_id;
		leafIdMap.put(max_id++,l);
	}

}
