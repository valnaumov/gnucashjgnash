/*
 * Copyright 2017 Albert Santos.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
*/
package gnucashjgnash.imports;

import gnucashjgnash.imports.GnuCashToJGnashContentHandler.AbstractStateHandler;
import gnucashjgnash.imports.GnuCashToJGnashContentHandler.StateHandler;

/**
 * @author albert
 *
 */
public class IntEntry {
	int value;
	String parseError;
	boolean wasParsed;
	
	
	public boolean validateParse(StateHandler stateHandler, String qName) {
		if (this.parseError != null) {
			stateHandler.recordWarning("IntValueInvalid_" + qName, "Message.Parse.XMLIntValueInvalid", qName);
			return false;
		}
		return true;
	}

	
	public static class IntEntryStateHandler extends AbstractStateHandler {
		final IntEntry intEntry;
		
		IntEntryStateHandler(IntEntry intEntry, GnuCashToJGnashContentHandler contentHandler, StateHandler parentStateHandler,
				String elementName) {
			super(contentHandler, parentStateHandler, elementName);
			this.intEntry = intEntry;
		}

		/* (non-Javadoc)
		 * @see gnucashjgnash.imports.GnuCashToJGnashContentHandler.AbstractStateHandler#endState()
		 */
		@Override
		protected void endState() {
			super.endState();
			
			try {
				this.intEntry.value = Integer.parseInt(this.characters);
				this.intEntry.wasParsed = true;
			}
			catch (NumberFormatException e) {
				this.intEntry.parseError = e.getLocalizedMessage();
			}
		}
		
	}
}