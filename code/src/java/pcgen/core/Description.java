/*
 * AbilityInfoPanel.java
 * Copyright 2006 (C) Aaron Divinsky <boomer70@yahoo.com>
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 2.1 of the License, or (at your option) any later version.
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this library; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA 02111-1307 USA
 *
 * Current Ver: $Revision$
 * Last Editor: $Author: $
 * Last Edited: $Date$
 */
package pcgen.core;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

import pcgen.base.lang.StringUtil;
import pcgen.cdom.base.ConcretePrereqObject;
import pcgen.cdom.base.Constants;
import pcgen.cdom.content.CNAbility;
import pcgen.cdom.enumeration.Nature;
import pcgen.io.EntityEncoder;
import pcgen.persistence.lst.output.prereq.PrerequisiteWriter;
import pcgen.util.Logging;

/**
 * This class represents a generic description field.
 * 
 * <p>The class supports the description object having one or more prerequisites
 * as well as performing variable substitution on the string itself.
 * 
 * <p>Variable substitution is performed by replacing a placeholder indicated
 * by %# with the #th variable in the variable list.  For example, the string
 * <br /><code>&quot;This is %1 variable %3 %2&quot;</code>
 * <br />would be replaced with the string &quot;This is a variable substitution
 * string&quot; if the variable list was &quot;a&quot;,&quot;string&quot;, 
 * &quot;substitution&quot;.
 * 
 * @author boomer70 <boomer70@yahoo.com>
 * 
 * @since 5.11.1
 */
public class Description extends ConcretePrereqObject
{
	private List<String> theComponents = new ArrayList<String>();
	private List<String> theVariables = null;
	
	private static final String VAR_NAME = "%NAME"; //$NON-NLS-1$
	private static final String VAR_CHOICE = "%CHOICE"; //$NON-NLS-1$
	private static final String VAR_LIST = "%LIST"; //$NON-NLS-1$
	private static final String VAR_FEATS = "%FEAT="; //$NON-NLS-1$
	
	private static final String VAR_MARKER = "$$VAR:"; //$NON-NLS-1$
	
	/**
	 * Default constructor.
	 * 
	 * @param aString The description string.
	 */
	public Description( final String aString )
	{
		int currentInd = 0;
		int percentInd = -1;
		while ( (percentInd = aString.indexOf('%', currentInd)) != -1 )
		{
			final String preText = aString.substring(currentInd, percentInd);
			if ( preText.length() > 0 )
			{
				theComponents.add(preText);
			}
			if ( percentInd == aString.length() - 1)
			{
				theComponents.add("%"); //$NON-NLS-1$
				return;
			}
			if ( aString.charAt(percentInd + 1) == '{' )
			{
				// This is a bracketed placeholder.  The replacement parameter
				// is contained within the {}
				currentInd = aString.indexOf('}', percentInd + 1) + 1;
				final String replacement = aString.substring(percentInd + 1, currentInd);
				// For the time being we will only support numerics here.
				try
				{
					Integer.parseInt(replacement);
				}
				catch (NumberFormatException nfe )
				{
					Logging.errorPrintLocalised("Errors.Description.InvalidVariableReplacement", replacement); //$NON-NLS-1$
				}
				theComponents.add(VAR_MARKER + replacement);
			}
			else if ( aString.charAt(percentInd + 1) == '%' )
			{
				// This is an escape sequence so we can actually print a %
				currentInd = percentInd + 2;
				theComponents.add("%"); //$NON-NLS-1$
			}
			else
			{
				// In this case we have an unbracketed placeholder.  We will
				// walk the string until such time as we no longer have a number
				currentInd = percentInd + 1;
				while ( currentInd < aString.length() )
				{
					final char val = aString.charAt(currentInd);
					try
					{
						Integer.parseInt(String.valueOf(val));
						currentInd++;
					}
					catch (NumberFormatException nfe)
					{
						break;
					}
				}
				if ( currentInd > percentInd + 1 )
				{
					theComponents.add(VAR_MARKER + aString.substring(percentInd+1, currentInd));
				}
				else
				{
					// We broke out of the variable finding loop without finding
					// even a single integer.  Assume we have a DESC field that
					// is using a % unescaped.
					theComponents.add(aString.substring(percentInd, percentInd+1));
					Logging.log(
						Logging.LST_WARNING,
						"The % without a number in the description '"
							+ aString
							+ "' should be either escaped e.g. %% or made into a parameter reference e.g. %1 .");
				}
			}
		}
		theComponents.add(aString.substring(currentInd));
	}
	
	/**
	 * Adds a variable to use in variable substitution.
	 * 
	 * @param aVariable
	 */
	public void addVariable( final String aVariable )
	{
		if ( theVariables == null )
		{
			theVariables = new ArrayList<String>();
		}
		theVariables.add( aVariable );
	}
	
	/**
	 * Gets the description string after having tested all prereqs and 
	 * substituting all variables.
	 * 
	 * @param aPC The PlayerCharacter used to evaluate formulas.
	 * 
	 * @return The fully substituted description string.
	 */
	public String getDescription( final PlayerCharacter aPC, List<? extends Object> objList )
	{
		if (objList.size() == 0)
		{
			return Constants.EMPTY_STRING;
		}
		PObject sampleObject;
		Object b = objList.get(0);
		if (b instanceof PObject)
		{
			sampleObject = (PObject) b;
		}
		else if (b instanceof CNAbility)
		{
			sampleObject = ((CNAbility) b).getAbility();
		}
		else
		{
			Logging
				.errorPrint("Unable to resolve Description with object of type: "
					+ b.getClass().getName());
			return Constants.EMPTY_STRING;
		}
		
		final StringBuilder buf = new StringBuilder();
		if (this.qualifies(aPC, sampleObject))
		{
			for ( final String comp : theComponents )
			{
				if ( comp.startsWith(VAR_MARKER) )
				{
					final int ind = Integer.parseInt(comp.substring(VAR_MARKER.length()));
					if ( theVariables == null || ind > theVariables.size() )
					{
						continue;
					}
					final String var = theVariables.get(ind - 1);
					if ( var.equals(VAR_NAME) )
					{
						if ( sampleObject != null )
						{
							buf.append(sampleObject.getOutputName());
						}
					}
					else if ( var.equals(VAR_CHOICE) )
					{
						Object obj = objList.get(0);
						PObject object;
						if (obj instanceof PObject)
						{
							object = (PObject) b;
						}
						else if (obj instanceof CNAbility)
						{
							CNAbility cna = (CNAbility) obj;
							object = aPC.getPCAbility(cna);
							object = (object == null) ? cna.getAbility() : object;
						}
						else
						{
							Logging
								.errorPrint("In Description resolution, "
									+ "Ignoring object of type: "
									+ b.getClass().getName());
							continue;
						}
						if (aPC.hasAssociations(object))
						{
							buf.append(aPC.getAssociationList(object).get(0));
						}
					}
					else if ( var.equals(VAR_LIST) )
					{
						List<String> assocList = new ArrayList<String>();
						for (Object obj : objList)
						{
							PObject object;
							if (obj instanceof PObject)
							{
								object = (PObject) b;
							}
							else if (obj instanceof CNAbility)
							{
								CNAbility cna = (CNAbility) obj;
								object = aPC.getPCAbility(cna);
								object = (object == null) ? cna.getAbility() : object;
							}
							else
							{
								Logging
									.errorPrint("In Description resolution, "
										+ "Ignoring object of type: "
										+ b.getClass().getName());
								continue;
							}
							assocList.addAll(aPC.getAssociationList(object));
						}
						String joinString;
						if (assocList.size() == 2)
						{
							joinString = " and ";
						}
						else
						{
							joinString = ", ";
						}
						buf.append(StringUtil.joinToStringBuilder(assocList,
							joinString));
					}
					else if ( var.startsWith(VAR_FEATS) )
					{
						final String featName = var.substring(VAR_FEATS.length());
						List<CNAbility> feats;
						if (featName.startsWith("TYPE=") || featName.startsWith("TYPE."))
						{
							feats = getAllFeats(aPC);
						}
						else
						{
							Ability feat =
									Globals.getContext().ref
										.silentlyGetConstructedCDOMObject(
											Ability.class,
											AbilityCategory.FEAT, featName);
							if (feat == null)
							{
								Logging
									.errorPrint("Found invalid Feat reference in Description: "
										+ featName);
							}
							feats = getFeats(aPC, feat);
						}
						boolean needSpace = false;
						for ( final CNAbility cna : feats )
						{
							if (cna.getAbility().isType(featName.substring(5)))
							{
								if (needSpace)
								{
									buf.append(' ');
								}
								buf.append(aPC.getDescription(Collections.singletonList(cna)));
								needSpace = true;
							}
						}
					}
					else if ( var.startsWith("\"") ) //$NON-NLS-1$
					{
						buf.append(var.substring(1, var.length() - 1));
					}
					else
					{
						buf.append(aPC.getVariableValue(var, "Description").intValue()); //$NON-NLS-1$
					}
				}
				else
				{
					buf.append(comp);
				}
			}
		}
		return buf.toString();
	}
	
	private List<CNAbility> getFeats(PlayerCharacter pc, Ability a)
	{
		final List<CNAbility> listOfAbilities = new ArrayList<CNAbility>();
		Collection<AbilityCategory> allCats =
				SettingsHandler.getGame().getAllAbilityCategories();
		for (AbilityCategory aCat : allCats)
		{
			if (aCat.getParentCategory().equals(AbilityCategory.FEAT))
			{
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.NORMAL));
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.AUTOMATIC));
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.VIRTUAL));
			}
		}
		return listOfAbilities;
	}

	private List<CNAbility> getAllFeats(PlayerCharacter pc)
	{
		List<CNAbility> listOfAbilities = new ArrayList<CNAbility>();
		Collection<AbilityCategory> allCats =
				SettingsHandler.getGame().getAllAbilityCategories();
		for (AbilityCategory aCat : allCats)
		{
			if (aCat.getParentCategory().equals(AbilityCategory.FEAT))
			{
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.NORMAL));
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.AUTOMATIC));
				listOfAbilities.addAll(pc.getCNAbilities(aCat, Nature.VIRTUAL));
			}
		}
		return listOfAbilities;
	}

	/**
	 * Gets the Description tag in PCC format.
	 * 
	 * @return A String in LST file format for this description.
	 * 
	 * @see pcgen.cdom.base.PrereqObject#getPCCText()
	 */
	public String getPCCText()
	{
		final StringBuilder buf = new StringBuilder();
		
		for ( final String str : theComponents )
		{
			if ( str.startsWith(VAR_MARKER) )
			{
				final int ind = Integer.parseInt(str.substring(VAR_MARKER.length()));
				buf.append('%' + String.valueOf(ind));
			}
			else if (str.equals("%"))
			{
				//reescape
				buf.append("%%");
			}
			else
			{
				buf.append(EntityEncoder.encodeLight(str));
			}
		}
		if ( theVariables != null )
		{
			for ( final String var : theVariables )
			{
				buf.append(Constants.PIPE);
				buf.append(var);
			}
		}
		
		if (hasPrerequisites())
		{
			buf.append(Constants.PIPE);
			buf.append(new PrerequisiteWriter().getPrerequisiteString(
					getPrerequisiteList(), Constants.PIPE));
		}
		return buf.toString();
	}
	
	@Override
	public String toString()
	{
		return getPCCText();
	}


	@Override
	public int hashCode()
	{
		return theComponents.size() + 7 * getPrerequisiteCount() + 31
			* (theVariables == null ? 0 : theVariables.size());
	}

	@Override
	public boolean equals(Object o)
	{
		if (o == this)
		{
			return true;
		}
		if (!(o instanceof Description))
		{
			return false;
		}
		Description other = (Description) o;
		if (theVariables == null)
		{
			if (other.theVariables != null)
			{
				return false;
			}
		}
		return theComponents.equals(other.theComponents)
			&& (theVariables == null || theVariables.equals(other.theVariables))
			&& equalsPrereqObject(other);
	}

}
