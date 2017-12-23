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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.xml.sax.Attributes;

import gnucashjgnash.imports.GnuCashToJGnashContentHandler.SimpleDataStateHandler;
import gnucashjgnash.imports.GnuCashToJGnashContentHandler.StateHandler;
import jgnash.engine.Account;
import jgnash.engine.AccountGroup;
import jgnash.engine.AccountType;
import jgnash.engine.ReconciledState;
import jgnash.engine.SecurityNode;


/**
 * Represents a parsed GnuCash Split from <a href="https://github.com/Gnucash/gnucash/blob/master/libgnucash/doc/xml/gnucash-v2.rnc" target="_blank" rel="noopener noreferrer">gnucash-v2.rnc</a>
 * @author albert
 *
 */
public class SplitEntry {
    IdEntry id = new IdEntry();
    String memo;
    String action;
    String reconciledState;
    TimeEntry reconcileDate = new TimeEntry();
    NumericEntry value = new NumericEntry();
    NumericEntry quantity = new NumericEntry();
    IdEntry account = new IdEntry();
    IdEntry lot = new IdEntry();
    Map<String, SlotEntry> slots = new HashMap<>();
    
    Account jGnashAccount;
    ReconciledState jGnashReconciledState;
    
    SecurityNode jGnashSecurity;
    
    
    public boolean validateForJGnash(GnuCashToJGnashContentHandler contentHandler) {
        this.jGnashAccount = contentHandler.jGnashAccounts.get(this.account.id);
        if (this.jGnashAccount == null) {
            this.jGnashSecurity = contentHandler.jGnashSecuritiesByStockAccountId.get(this.account.id);
            if (this.jGnashSecurity == null) {
                contentHandler.recordWarning("SplitAccountMissing_" + this.account.id, "Message.Warning.SplitAccountMissing", this.id.id, this.account.id);
                return false;
            }
            else {
                AccountImportEntry accountEntry = contentHandler.accountImportEntries.get(this.account.id);
                this.jGnashAccount = contentHandler.jGnashAccounts.get(accountEntry.parentId.id);
                if (this.jGnashAccount == null) {
                    contentHandler.recordWarning("SplitSecurityAccountParentMissing_" + this.account.id, "Message.Warning.SplitSecurityAccountParentMissing", this.id.id, this.account.id);
                    return false;
                }
            }
        }
        else {
            if (this.jGnashAccount.memberOf(AccountGroup.INVEST)) {
                this.jGnashSecurity = contentHandler.jGnashSecuritiesByStockAccountId.get(this.account.id);
            }
        }
        
        switch (this.reconciledState) {
        case "y":
            jGnashReconciledState = ReconciledState.RECONCILED;
            break;
            
        case "c":
            jGnashReconciledState = ReconciledState.CLEARED;
            break;
            
        case "n" :
            jGnashReconciledState = ReconciledState.NOT_RECONCILED;
            break;
            
        default :
            contentHandler.recordWarning("SplitReconciledStateNotSupported_" + this.reconciledState, "Message.Warning.SplitReconciledStateNotSupported",
                    this.id.id, this.reconciledState);
            return false;
        }

        return true;        
    }

    
    /**
     * {@link StateHandler} for Split+ from <a href="https://github.com/Gnucash/gnucash/blob/master/libgnucash/doc/xml/gnucash-v2.rnc" target="_blank" rel="noopener noreferrer">gnucash-v2.rnc</a>
     * @author albert
     *
     */
    static class SplitsStateHandler extends GnuCashToJGnashContentHandler.AbstractStateHandler {
        final Map<String, SplitEntry> splitEntries;
        final List<SplitEntry> splitEntriesList;
        final String splitElementName;
        SplitsStateHandler(final Map<String, SplitEntry> splitEntries, final List<SplitEntry> splitEntriesList, final String splitElementName,
                            GnuCashToJGnashContentHandler contentHandler, GnuCashToJGnashContentHandler.StateHandler parentStateHandler,
                            String elementName) {
            super(contentHandler, parentStateHandler, elementName);
            this.splitEntries = splitEntries;
            this.splitEntriesList = splitEntriesList;
            this.splitElementName = splitElementName;
        }
        
        /* (non-Javadoc)
         * @see gnucashjgnash.imports.GnuCashToJGnashContentHandler.AbstractStateHandler#getStateHandlerForElement(java.lang.String)
         */
        @Override
        protected StateHandler getStateHandlerForElement(String qName) {
            if (qName.equals(this.splitElementName)) {
                return new SplitStateHandler(this.splitEntries, this.splitEntriesList, this.contentHandler, this, qName); 
            }
            
            return super.getStateHandlerForElement(qName);
        }
        
        
    }


    /**
     * {@link StateHandler} for a Split from <a href="https://github.com/Gnucash/gnucash/blob/master/libgnucash/doc/xml/gnucash-v2.rnc" target="_blank" rel="noopener noreferrer">gnucash-v2.rnc</a>
     * @author albert
     *
     */
    static class SplitStateHandler extends GnuCashToJGnashContentHandler.AbstractStateHandler {
        final Map<String, SplitEntry> splitEntries;
        final List<SplitEntry> splitEntriesList;
        final SplitEntry splitEntry = new SplitEntry();

        SplitStateHandler(final Map<String, SplitEntry> splitEntries, final List<SplitEntry> splitEntriesList, 
                        GnuCashToJGnashContentHandler contentHandler, GnuCashToJGnashContentHandler.StateHandler parentStateHandler,
                        String elementName) {
            super(contentHandler, parentStateHandler, elementName);
            this.splitEntries = splitEntries;
            this.splitEntriesList = splitEntriesList;
        }

        /* (non-Javadoc)
         * @see gnucashjgnash.imports.GnuCashToJGnashContentHandler.AbstractStateHandler#getStateHandlerForElement(java.lang.String)
         */
        @Override
        protected StateHandler getStateHandlerForElement(String qName) {
            switch (qName) {
            case "split:id" :
                return new IdEntry.IdStateHandler(this.splitEntry.id, this.contentHandler, this, qName);
                
            case "split:memo" : 
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setSplitEntryField(SplitEntry splitEntry, String value) {
                            splitEntry.memo = value;
                        }
                    });
                
            case "split:action" : 
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setSplitEntryField(SplitEntry splitEntry, String value) {
                            splitEntry.action = value;
                        }
                    });
                
            case "split:reconciled-state" : 
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setSplitEntryField(SplitEntry splitEntry, String value) {
                            splitEntry.reconciledState = value;
                        }
                    });
                
            case "split:reconcile-date" :
                return new TimeEntry.TimeStateHandler(this.splitEntry.reconcileDate, this.contentHandler, this, qName);
                
            case "split:value" : 
                return new NumericEntry.NumericStateHandler(this.splitEntry.value, this.contentHandler, this, qName);
                
            case "split:quantity" : 
                return new NumericEntry.NumericStateHandler(this.splitEntry.quantity, this.contentHandler, this, qName);
                
            case "split:account" : 
                return new IdEntry.IdStateHandler(this.splitEntry.account, this.contentHandler, this, qName);
                
            case "split:lot":
                // TODO: Split lots
                break;
                
            case "split:slots" : 
                return new SlotEntry.SlotsStateHandler(this.splitEntry.slots, this.contentHandler, this, qName);
            }
            return super.getStateHandlerForElement(qName);
        }

        @Override
        protected void endState() {
            super.endState();
            
            if (!this.splitEntry.id.validateGUIDParse(this, "split:id")) {
                return;
            }
            if (this.splitEntry.reconciledState == null) {
                recordWarning("SplitReconciledStateMissing", "Message.Parse.XMLSplitReconciledStateMissing", this.elementName, "split:reconciled-state");
                return;
            }
            if (!this.splitEntry.value.validateParse(this, "split:value")) {
                return;
            }
            if (!this.splitEntry.quantity.validateParse(this, "split:quantity")) {
                return;
            }
            if (!this.splitEntry.account.validateGUIDParse(this, "split:account")) {
                return;
            }
            
            if (this.splitEntries.put(this.splitEntry.id.id, this.splitEntry) != null) {
                recordWarning("DuplicateSplitEntries", "Message.Parse.XMLDuplicateSplitEntries", this.splitEntry.id.id);
            }
            if (this.splitEntriesList != null) {
                this.splitEntriesList.add(this.splitEntry);
            }
        }
    }

    static abstract class SimpleDataSetterImpl extends GnuCashToJGnashContentHandler.AbstractSimpleDataSetter {
        @Override
        public void setAttributes(Attributes atts, GnuCashToJGnashContentHandler.SimpleDataStateHandler stateHandler) {
            SplitStateHandler parentStateHandler = (SplitStateHandler)stateHandler.parentStateHandler;
            setSplitEntryAttributes(parentStateHandler.splitEntry, atts);
        }

        @Override
        public void setData(String characters, GnuCashToJGnashContentHandler.SimpleDataStateHandler stateHandler) {
            SplitStateHandler parentStateHandler = (SplitStateHandler)stateHandler.parentStateHandler;
            setSplitEntryField(parentStateHandler.splitEntry, characters);
        }

        protected void setSplitEntryAttributes(SplitEntry splitEntry, Attributes attr) {

        }
        protected abstract void setSplitEntryField(SplitEntry splitEntry, String value);
    }
     
}
