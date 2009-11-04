/*
 * Copyright 2008 Novamente LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package relex.entity;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import relex.feature.FeatureNode;
import relex.feature.LinkableView;
import relex.feature.SemanticView;
import relex.output.RawView;

public class EntityMaintainer implements Serializable 
{
	private static final long serialVersionUID = -8186219027158709715L;

	/**
	 * This number is derived from the LinkParser dictionary -- 
	 * it only accepts suffixes up to "60." To increase this 
	 * number, you need to add more words to the Link Parser 
	 * dictionary, like "genericID61", "dateID61", etc.
	 */
	public static int MAX_NUM_ENTITIES = 60;

	// The original sentence string
	private String originalSentence;

	// The converted sentence is a copy of the original, 
	// except all entities are replaced with ID strings
	private String convertedSentence;

    // An array of EntityInfos, ordered by their order in the sentence
    public List<EntityInfo> orderedEntityInfos;

    // Maps entity ID strings to EntityInfos
    private Map<String,EntityInfo> iDs2Entities;

    // Keeps track of the last entityIDIndex created
    int entityIDIndex;

    // Maps feature nodes to entity IDs
    // private HashMap<FeatureNode, String> featureNodes2EntityIDs;

    // A set of integer indexes of inserted whitespace characters
    private Set<Integer> insertedWhitespaceCharIndexes;

    static List<String> emolist = new ArrayList<String>();
	
	static
	{
		// Emoticons -- smiley faces, right :-)
		// Partial list, only of the basics, taken from wikipedia
		// This could be improved on by automatically generating
		// these with and without noses, etc. 
		//
		// More generally, we should have a "bogus punctutation entity"
		// for any sort of markup that is not recognized here.
		//
		// Some of these have leading or trailing whitespace ... 
		// these are typically ones that have numbers, that's so that
		// number expressions don't get mangled; others which might get
		// mistaken for initials.
		//
		emolist.add(":-)");
		emolist.add(":-(");
		emolist.add(":)");
		emolist.add(":(");
		emolist.add(":'-)");
		emolist.add(":')");
		emolist.add(":D");
		emolist.add(":-D");
		emolist.add(":-O");
		emolist.add(":-S");
		emolist.add(":-$");
		emolist.add(":-*");
		emolist.add(":[");
		emolist.add(":'[");
		emolist.add(":'\\");
		emolist.add(":-B");
		emolist.add(":-#");
		emolist.add(":-|");
		emolist.add(":-&");
		emolist.add(":-X");
		emolist.add(":-K");
		emolist.add(":]");
		emolist.add(":-@");
		emolist.add(":@");
		emolist.add(":O]");
		emolist.add(":d");
		emolist.add("|-O");
		emolist.add("%-(");
		emolist.add("=)");
		emolist.add("=O");
		emolist.add(";)");
		emolist.add(";-)");
		emolist.add(";]");
		emolist.add(";O]");
		emolist.add(";O");
		emolist.add(";D");
		emolist.add("B-)");
		emolist.add(" T.T "); // XXX Danger, may be initials
		emolist.add("`:-)");
		emolist.add(":P");
		emolist.add("O:-)");
		emolist.add("><");
		emolist.add(">_<");
		emolist.add("<_<");
		emolist.add(">_>");
		emolist.add(" Oo "); 
		emolist.add(">:D");
		emolist.add(" e.e "); // XXX Danger, may be initials
		emolist.add("-.-*");
		emolist.add("~.^");
		emolist.add("(-_-)");
		emolist.add("(-.-)");
		emolist.add("-.-'");
		emolist.add(" E.E "); // XXX Danger, may be initials
		emolist.add("-.O");
		emolist.add("*o*");
		emolist.add("=^.^=");
		emolist.add(" 8)");  // XXX Danger! may be legit non-smiley
		emolist.add(" 8D "); // XXX Danger! may be legit non-smiley
		emolist.add(">O");
		emolist.add("(:-D");
		emolist.add("c^:3");
		emolist.add("~:>");
		emolist.add("x-(");
		emolist.add(";:^)B>");
		emolist.add(" O.O "); // XXX Danger, may be initials
		emolist.add(" o.o ");
		emolist.add(" O.o ");
		emolist.add(" o.O ");
		emolist.add(" 8| ");   // XXX Daner, may be numerical expr.
		emolist.add(">8V-()<");
		emolist.add(" =3 ");  // XXX Danger, may be part of formula
		emolist.add("-:3");
		emolist.add(" <3 ");  // XXX Danger, may be part of formula
		emolist.add("<><");
		emolist.add("<@:)");
		emolist.add(":3=");
	}

	// --------------------------------------------------------
	/**
	 * Returns true iff the character after the entity is legal.
	 * Only whitespace and certain punction are allowed.
	 */
	private boolean isLegalEntityFollowingString(String s)
	{
		if (s.length() == 0)
			return true;
		char c = s.charAt(0);
		if (Character.isWhitespace(c))
			return true;
		if (c == '.' || c == ',' || c == ';' || c == ':')
			return true;
		if (s.startsWith("'s ") || s.startsWith("' "))
			return true;
		return false;
	}

	/**
	 * Creates the converted sentence. The converted sentence 
	 * is a copy of the original, except all entities are 
	 * replaced with ID strings.
	 */
	private void createConvertedSentence()
	{
		if ((orderedEntityInfos == null) || (orderedEntityInfos.size() == 0))
			convertedSentence = originalSentence;
		convertedSentence = "";
		int curIndex = 0;

		// For each entity...
		for(EntityInfo eInfo : orderedEntityInfos)
		{
			if (eInfo.getFirstCharIndex() < curIndex)
			{
				if (eInfo.getFirstCharIndex() == curIndex - 1)
				{
					curIndex = eInfo.getFirstCharIndex();
				}
				else
				{
					System.err.println(
						"Error: Entity start is at an unexpected location:\n" +
						"Sentence= " + originalSentence + "\n" +
						"firt char= " + eInfo.getFirstCharIndex() +
						" curidx= " + curIndex + "\n");
					continue;
				}
			}

			// Copy the preceeding portion (if any) of the original string.
			convertedSentence += originalSentence.substring(curIndex, eInfo
					.getFirstCharIndex());

			// Insert leading white space before eInfo if it is 
			// not preceeded by white space.
			if (convertedSentence.length() > 0
					&& !Character.isWhitespace(convertedSentence
							.charAt(convertedSentence.length() - 1)))
			{
				convertedSentence += ' ';
				insertedWhitespaceCharIndexes.add(
					new Integer(convertedSentence.length() - 1));
			}

			curIndex = eInfo.getLastCharIndex();

			// If the entity ends with a period, then sometimes the
			// entity detector doesn't include the period as part of 
			// the entity. Fix this up (as long as the period is not
			// at the enbd of the sentence).
			// For example: "The A.D.A. advises against this."
			if ((originalSentence.length() > curIndex) &&
			    (originalSentence.charAt(eInfo.getLastCharIndex()) == '.'))
			{
				eInfo.setLastCharIndex(eInfo.getLastCharIndex()+1);
				curIndex++;
			}

			// Insert the ID string of the entity.
			convertedSentence += makeID(eInfo);

			// If the entity is at the end of the sentence, then the 
			// entity detector may swallow the period at the end 
			// of the sentence. This can confuse downstream handlers,
			// so add a new period to end the sentence.
			// For example: "It is located in Wshington, D.C."
			if ((originalSentence.length() == curIndex) &&
			    (originalSentence.charAt(eInfo.getLastCharIndex()-1) == '.'))
			{
				convertedSentence += '.';
				insertedWhitespaceCharIndexes.add(
					new Integer(convertedSentence.length() - 1));
			}

			// Insert trailing white space after eInfo if it is 
			// not followed by a legal string
			if (originalSentence.length() > curIndex
					&& !isLegalEntityFollowingString(originalSentence
							.substring(curIndex)))
			{
				convertedSentence += ' ';
				insertedWhitespaceCharIndexes.add(
					new Integer(convertedSentence.length() - 1));
			}
		}

		// Copy the remaining portion (if any) of the original string.
		if (curIndex < originalSentence.length())
			convertedSentence += originalSentence.substring(curIndex);

		// System.out.println("CONVERSION\n" + originalSentence + "\n" +
		// convertedSentence);
	}

	private String makeID(EntityInfo eInfo)
	{
		++entityIDIndex;
		String id = eInfo.idStringPrefix() + entityIDIndex;
		iDs2Entities.put(id, eInfo);
		return id;
	}

	// --------------------------------------------------------
	/**
	 * Strip out emoticons, smileys :-)
	 */
	private void identifyEmoticons()
	{
		for(String emo : emolist)
		{
			int start = originalSentence.indexOf(emo);
			if (start < 0) continue;
			int end = start + emo.length();
	
			EntityInfo ei = new EntityInfo(originalSentence, start, end, EntityType.EMOTICON);
			addEntity(ei);
		}
	}

	/**
	 * Escape parenthesis, treating them as entities.
	 * This is needed for one reason only: the phrase markup
	 * uses a LISP-like structure for the Penn-treebank markup,
	 * and stray parens in the original sentence mess it up.
	 */
	private void escapePunct(char punct)
	{
		int start = 0;
		while (true)
		{
			start = originalSentence.indexOf(punct, start);
			if (start < 0) break;

			EntityInfo ei = new EntityInfo(originalSentence, start, start+1, EntityType.PUNCTUATION);
			addEntity(ei);
			start++;
		}
	}

	private void escapeParens()
	{
		escapePunct('(');
		escapePunct(')');
		escapePunct('[');
		escapePunct(']');
	}

	// --------------------------------------------------------
	
	/**
	 * Add the entity info to the list, inserting it in sorted order.
	 */
	public void addEntity(EntityInfo ei)
	{
		int open = 0;
		int start = ei.getFirstCharIndex();
		int end = ei.getLastCharIndex();
		for (EntityInfo e: orderedEntityInfos)
		{
			int beg = e.getFirstCharIndex();
			if ((open <= start) && (end <= beg))
			{
				int idx = orderedEntityInfos.indexOf(e);
				orderedEntityInfos.add(idx, ei);
				return;
			}
			open = e.getLastCharIndex();

			// If our entity overlaps with existing entities, ignore it.
			if (start < open) return;
		}
		orderedEntityInfos.add(ei);
	}

	// --------------------------------------------------------

	/**
	 * Default constructor is mainly used for de-serialization purposes.
	 */
	public EntityMaintainer()
	{
	    
	}
	
	public EntityMaintainer(String _originalSentence, Collection<EntityInfo> eis)
	{
		if (eis.size() > MAX_NUM_ENTITIES)
		{
			System.err.println("WARNING: Sentence had more than "
					+ MAX_NUM_ENTITIES
					+ ".  Ignoring extras.\nOriginal sentence:"
					+ originalSentence);
		}
		originalSentence = _originalSentence;
		orderedEntityInfos = new ArrayList<EntityInfo>();

		for (EntityInfo it : eis)
		{
			addEntity(it);
		}

		// Strip out emoticons, which GATE doesn't do.
		// Emoticons confuse the parser.
		identifyEmoticons();

		// Escape parenthesis. These confuse the phrase-tree markup.
		escapeParens();

		iDs2Entities = new HashMap<String, EntityInfo>();
		entityIDIndex = 0; // the first used index will be '1'

		insertedWhitespaceCharIndexes = new TreeSet<Integer>();
		createConvertedSentence();
	}

	// --------------------------------------------------------

	public boolean isEntityID(String e)
	{
		return iDs2Entities.containsKey(e);
	}

	public EntityInfo getEntityInfo(String entityID)
	{
		return iDs2Entities.get(entityID);
	}

	private int previouslyInsertedWhitespace(int index)
	{
		int insertedSoFar = 0;
		Iterator<Integer> inserts = insertedWhitespaceCharIndexes.iterator();
		while (inserts.hasNext()) {
			if (inserts.next().intValue() < index)
				++insertedSoFar;
		}
		return insertedSoFar;
	}

	/**
	 * Replaces all feature values containing entityIDs, so that they now
	 * contain original strings. Stores the FeatureNodes in a map which maps
	 * them to entityIDs. Does not repair char index values in the word nodes
	 */
	public void repairSentence(FeatureNode leftNode)
	{
		int charDelta = 0;
		try
		{
			for (LinkableView word = new LinkableView(leftNode);
			     word != null;
			     word = (word.getNext() == null ?
			                  null : new LinkableView(word.getNext())))
			{
				int previousWhiteSpace = 
				      previouslyInsertedWhitespace(word.getStartChar());

				String wordName = word.getWordString();

				word.setStartChar(word.getStartChar() + charDelta
						- previousWhiteSpace);

				// word.setExpandedStartChar(word.getExpandedStartChar()+charDelta);

				if (isEntityID(wordName))
				{
					EntityInfo entInfo = getEntityInfo(wordName);
					String origName = entInfo.getOriginalString();
					charDelta += origName.length() - wordName.length();
					try
					{
						SemanticView semView = new SemanticView(word.fn()
								.get("ref"));
						semView.setName(origName);
					}
					catch (Exception e) {}
				}

				word.setEndChar(word.getEndChar() + charDelta - previousWhiteSpace);
				// word.setExpandedEndChar(word.getExpandedEndChar()+charDelta);
			}
		}
		catch (Exception e)
		{
			System.err.println("Error: failed to repair sentence.");
			System.err.println("Broken sentence was: "+getOriginalSentence());
			System.err.println(RawView.printZHeads(leftNode));
			e.printStackTrace();
		}
	}
    
	/**
	 * prepareSentence() -- markup parsed sentence with entity 
	 * information. This needs to be done before the relex algs run,
	 * as the relex algs may use some of this information.
	 */
	public void prepareSentence(FeatureNode leftNode)
	{
		for (LinkableView word = new LinkableView(leftNode); 
		     word != null; 
		     word = (word.getNext() == null ? 
		                null : new LinkableView(word.getNext())))
		{
			String wordName = word.getWordString();
			if (isEntityID(wordName))
			{
				EntityInfo entInfo = getEntityInfo(wordName);
				entInfo.setProperties(word.fn());
			}
		}
	}

    /**
     * Return all EntityInfo's ordered by their starting character
     * position.
     */
    public List<EntityInfo> getEntities()
    {
        return orderedEntityInfos;
    }
    
    public void setEntities(List<EntityInfo> orderedEntityInfos)
    {
        this.orderedEntityInfos = orderedEntityInfos;
    }
    
    public String getConvertedSentence()
    {
        return convertedSentence;
    }

    public void setConvertedSentence(String convertedSentence)
    {
        this.convertedSentence = convertedSentence;
    }
    
    public String getOriginalSentence()
    {
        return originalSentence;
    }
	
    public void setOriginalSentence(String originalSentence)
    {
        this.originalSentence = originalSentence;
    }
        
	public Map<String, EntityInfo> getIDs2Entities()
    {
        return iDs2Entities;
    }

    public void setIDs2Entities(Map<String, EntityInfo> ds2Entities)
    {
        iDs2Entities = ds2Entities;
    }

    public int getEntityIDIndex()
    {
        return entityIDIndex;
    }

    public void setEntityIDIndex(int entityIDIndex)
    {
        this.entityIDIndex = entityIDIndex;
    }

    public Set<Integer> getInsertedWhitespaceCharIndexes()
    {
        return insertedWhitespaceCharIndexes;
    }

    public void setInsertedWhitespaceCharIndexes(Set<Integer> insertedWhitespaceCharIndexes)
    {
        this.insertedWhitespaceCharIndexes = insertedWhitespaceCharIndexes;
    }

    public String toString()
	{
		StringBuilder sb = new StringBuilder();
		for (EntityInfo info: orderedEntityInfos)
		{
			String name = info.getOriginalSentence().substring(
					info.getFirstCharIndex(), 
					info.getLastCharIndex());
			sb.append(makeID(info)).append(": ").append(name).append("\n");
		}
		return sb.toString();
	}

    public int hashCode()
    {
        return originalSentence == null ? 0 : originalSentence.hashCode();
    }
    
    public boolean equals(Object other)
    {
        if (! (other instanceof EntityMaintainer))
            return false;
        EntityMaintainer em = (EntityMaintainer)other;
        if (originalSentence == null)
            return em.originalSentence == null;
        else
            return this.originalSentence.equals(em.originalSentence) &&
                   this.orderedEntityInfos.equals(em.orderedEntityInfos); 
    }
    
	/**
	 * Arg0: a sentence with an entity Arg1: the first character of the
	 * entity Arg2: the last character of the entity Example:
	 * java relex.entity.EntityMaintainer "Does Mike think it will work?" 5 8
	 * Expected output: Does genericID1 think it will work?
	 */
	public static void main(String[] args)
	{
		String sentence = args[0];
		ArrayList<EntityInfo> list = new ArrayList<EntityInfo>();
		int arg = 1;
		while (args.length >= arg + 2)
		{
			EntityInfo eInfo = new EntityInfo(sentence,
								   Integer.parseInt(args[arg]),
								   Integer.parseInt(args[arg + 1]));
			list.add(eInfo);
			arg += 2;
		}
		EntityMaintainer em = new EntityMaintainer(sentence, list);
		System.out.println(em.getConvertedSentence());
	}
}