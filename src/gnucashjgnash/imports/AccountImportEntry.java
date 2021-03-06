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

import jgnash.engine.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

import gnucashjgnash.imports.GnuCashToJGnashContentHandler.StateHandler;
import gnucashjgnash.GnuCashConvertUtil;
import gnucashjgnash.NoticeTree.Source;
import gnucashjgnash.imports.CommodityEntry.CommodityRef;
import gnucashjgnash.imports.GnuCashToJGnashContentHandler.SimpleDataStateHandler;

/**
 * Represents a parsed GnuCash Account from <a href="https://github.com/Gnucash/gnucash/blob/master/libgnucash/doc/xml/gnucash-v2.rnc" target="_blank" rel="noopener noreferrer">gnucash-v2.rnc</a>.
 * @author albert
 *
 */
public class AccountImportEntry extends ParsedEntry {
    //private static final Logger LOG = Logger.getLogger(AccountImportEntry.class.getName());

    String name;
    IdEntry id = new IdEntry(this);
    String type;
    CommodityRef commodityRef = new CommodityRef();
    IntEntry commoditySCU = new IntEntry(this);
    String code;
    String description;
    IdEntry parentId = new IdEntry(this);
    Map<String, SlotEntry> slots = new HashMap<>();

    Map<String, AccountImportEntry> childAccountEntries = new HashMap<>();
    
    List<Transaction> jGnashTemplateTransactions = new ArrayList<>();
    
    
    public AccountImportEntry(GnuCashToJGnashContentHandler contentHandler) {
    	super(contentHandler);
    }
    

    /* (non-Javadoc)
	 * @see gnucashjgnash.imports.ParsedEntry#getParentSource()
	 */
	@Override
	public Source getParentSource() {
		if (this.parentId.id != null) {
			return this.contentHandler.accountImportEntries.get(this.parentId.id);
		}
		return null;
	}


	/* (non-Javadoc)
	 * @see gnucashjgnash.imports.ParsedEntry#getIndentifyingText(gnucashjgnash.imports.GnuCashToJGnashContentHandler)
	 */
	@Override
	public String getIndentifyingText(GnuCashToJGnashContentHandler contentHandler) {
		return GnuCashConvertUtil.getString("Message.ParsedEntry.AccountImportEntry", this.name);
	}


	/* (non-Javadoc)
	 * @see gnucashjgnash.imports.ParsedEntry#getGUID()
	 */
	@Override
	public String getUniqueId() {
		return GnuCashConvertUtil.getString("Message.ParsedEntry.AccountImportEntryId", this.id.id);
	}



	static class AccountStateHandler extends GnuCashToJGnashContentHandler.AbstractVersionStateHandler {
        final AccountImportEntry accountEntry;

        AccountStateHandler(GnuCashToJGnashContentHandler contentHandler, GnuCashToJGnashContentHandler.StateHandler parentStateHandler, String elementName) {
            super(contentHandler, parentStateHandler, elementName);
            this.accountEntry = new AccountImportEntry(contentHandler);
        }
        
		/* (non-Javadoc)
		 * @see gnucashjgnash.imports.GnuCashToJGnashContentHandler.StateHandler#getParsedEntry()
		 */
        @Override
        public ParsedEntry getParsedEntry() {
        	return this.accountEntry;
        }

        @Override
        protected boolean validateVersion(String version) {
            if (!version.equals("2.0.0")) {
                this.contentHandler.recordError("Message.Parse.XMLAccountVersionUnsupported", version);
                return false;
            }
            return true;
        }

        /* (non-Javadoc)
         * @see gnucashjgnash.imports.GnuCashToJGnashContentHandler.AbstractStateHandler#getStateHandlerForElement(java.lang.String)
         */
        @Override
        protected StateHandler getStateHandlerForElement(String qName) {
            switch (qName) {
            case "act:name" :
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setAccountEntryField(AccountImportEntry accountEntry, String value) {
                            accountEntry.name = value;
                        }
                    });

            case "act:id" :
                return new IdEntry.IdStateHandler(this.accountEntry.id, this.contentHandler, this, qName);

            case "act:type" :
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setAccountEntryField(AccountImportEntry accountEntry, String value) {
                            accountEntry.type = value;
                        }
                    });

            case "act:description" :
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setAccountEntryField(AccountImportEntry accountEntry, String value) {
                            accountEntry.description = value;
                        }
                    });

            case "act:code" :
                return new SimpleDataStateHandler(this.contentHandler, this, qName, new SimpleDataSetterImpl() {
                        @Override
                        protected void setAccountEntryField(AccountImportEntry accountEntry, String value) {
                            accountEntry.code = value;
                        }
                    });
                
            case "act:slots" :
                return new SlotEntry.SlotsStateHandler(this.accountEntry.slots, this.accountEntry, this.contentHandler, this, qName);
                
            case "act:parent" :
                return new IdEntry.IdStateHandler(this.accountEntry.parentId, this.contentHandler, this, qName);
                
            case "act:commodity" :
                return new CommodityEntry.CommodityRefStateHandler(this.accountEntry.commodityRef, this.contentHandler, this, qName);
                
            case "act:commodity-scu" :
            	return new IntEntry.IntEntryStateHandler(this.accountEntry.commoditySCU, this.contentHandler, this, qName);
                
            case "act:non-standard-scu" :
            	recordWarning("Message.Parse.XMLNonStandardSCUUnsupported");
            	break;
            
            case "act:lots" :
            	recordWarning("Message.Parse.XMLAccountLotsUnsupported");
            	break;
            }

            return super.getStateHandlerForElement(qName);
        }

        @Override
        protected void endState() {
            super.endState();

            if (this.ignoreChildElements) {
                return;
            }

            if (!this.accountEntry.id.validateGUIDParse(this, "act:id")) {
                return;
            }
            if (this.accountEntry.name == null) {
                recordWarning("Message.Parse.XMLAccountMissingElement", this.accountEntry.id, "act:name");
                return;
            }
            if (this.accountEntry.type == null) {
                recordWarning("Message.Parse.XMLAccountMissingElement", this.accountEntry.name, "act:type");
                return;
            }
            if (this.accountEntry.commoditySCU.isParsed) {
            }

            this.contentHandler.addAccountEntry(this.accountEntry);
        }
    }

    static abstract class SimpleDataSetterImpl extends GnuCashToJGnashContentHandler.AbstractSimpleDataSetter {
        @Override
        public void setData(String characters, GnuCashToJGnashContentHandler.SimpleDataStateHandler stateHandler) {
            AccountStateHandler parentStateHandler = (AccountStateHandler)stateHandler.parentStateHandler;
            setAccountEntryField(parentStateHandler.accountEntry, characters);
        }

        protected abstract void setAccountEntryField(AccountImportEntry accountEntry, String value);
    }


    /**
     * Called by {@link GnuCashToJGnashContentHandler} to build the child AccountImportEntry objects for this entry.
     * @param accountEntries
     */
    public void gatherChildAccountEntries(Map<String, AccountImportEntry> accountEntries) {
        for (Iterator<Map.Entry<String, AccountImportEntry>> iterator = accountEntries.entrySet().iterator(); iterator.hasNext(); ) {
            Map.Entry<String, AccountImportEntry> entry = iterator.next();
            AccountImportEntry accountEntry = entry.getValue();
            if ((accountEntry.parentId.id != null) && accountEntry.parentId.equals(this.id)) {
                this.childAccountEntries.put(entry.getKey(), accountEntry);
                iterator.remove();
            }
        }

        for (Map.Entry<String, AccountImportEntry> entry : this.childAccountEntries.entrySet()) {
            entry.getValue().gatherChildAccountEntries(accountEntries);
        }
    }


    /**
     * Called by {@link GnuCashToJGnashContentHandler} to generate the jGnash accounts for this and all the child AccountImportEntry objects.
     * @param contentHandler
     * @param engine
     * @param jGnashAccountEntries
     * @param accountIdsToIgnore
     * @return	<code>false</code> if this failed.
     */
    public boolean createJGnashAccounts(GnuCashToJGnashContentHandler contentHandler, Engine engine,
                                 Map<String, Account> jGnashAccountEntries, Set<String> accountIdsToIgnore) {
        // Can we generate the account?
        AccountType accountType = null;
        boolean hasSecurities = false;

        switch (this.type) {
            case "NONE" :
                accountIdsToIgnore.add(this.id.id);
                return true;

            case "BANK" :
                accountType = AccountType.BANK;
                break;

            case "CASH" :
                accountType = AccountType.CASH;
                break;

            case "CREDIT":
                accountType = AccountType.CREDIT;
                break;

            case "ASSET":
                accountType = AccountType.ASSET;
                break;

            case "LIABILITY":
                accountType = AccountType.LIABILITY;
                break;

            case "STOCK":
                accountType = AccountType.INVEST;
                hasSecurities = true;
                break;

            case "MUTUAL":
                accountType = AccountType.MUTUAL;
                hasSecurities = true;
                break;

            case "CURRENCY":
                break;

            case "INCOME":
                accountType = AccountType.INCOME;
                break;

            case "EXPENSE":
                accountType = AccountType.EXPENSE;
                break;

            case "EQUITY":
                accountType = AccountType.EQUITY;
                break;

            case "RECEIVABLE":
                contentHandler.recordWarning(this, "Message.Warning.UnsupportedAccountType", this.type);
                accountIdsToIgnore.add(this.id.id);
                return true;

            case "PAYABLE":
                contentHandler.recordWarning(this, "Message.Warning.UnsupportedAccountType", this.type);
                accountIdsToIgnore.add(this.id.id);
                return true;

            case "ROOT":
                accountType = AccountType.ROOT;
                break;

            case "TRADING":
                contentHandler.recordWarning(this, "Message.Warning.UnsupportedAccountType", this.type);
                accountIdsToIgnore.add(this.id.id);
                return true;

            case "CHECKING":
                accountType = AccountType.CHECKING;
                break;

            case "SAVINGS":
                accountType = AccountType.CHECKING;
                //contentHandler.recordWarning(this, "Message.Info.AccountMapped", this.type, "CHECKING");
                break;

            case "MONEYMRKT":
                accountType = AccountType.MONEYMKRT;
                break;

            case "CREDITLINE":
                contentHandler.recordWarning(this, "Message.Warning.UnsupportedAccountType", this.type);
                return true;

        }

        if (accountType == AccountType.ROOT) {
            jGnashAccountEntries.put(this.id.id, engine.getRootAccount());
        }
        else {
            Account parentAccount = null;
            if (this.parentId.id != null) {
                parentAccount = jGnashAccountEntries.get(this.parentId.id);
            }

            CurrencyNode currencyNode = getCurrencyNode(engine);

            Account newAccount = new Account(accountType, currencyNode);
            if (this.name != null) {
                newAccount.setName(this.name);
            }
            if (this.description != null) {
                newAccount.setDescription(this.description);
            }
            if (this.code != null) {
                try {
                    int code = Integer.parseInt(this.code);
                    newAccount.setAccountCode(code);
                }
                catch (NumberFormatException e) {
                    contentHandler.recordWarning(this, "Message.Warning.NonIntegerAccountCode", this.code);
                }
            }
            
            if ("true".equals(SlotEntry.getStringSlotValue(this.slots, "placeholder", null))) {
                newAccount.setPlaceHolder(true);
            }
            if ("true".equals(SlotEntry.getStringSlotValue(this.slots, "hidden", null))) {
                newAccount.setVisible(false);
            }
            
            if (hasSecurities) {
                if (!setupAccountSecurities(newAccount, contentHandler)) {
                    return false;
                }
            }

            engine.addAccount(parentAccount, newAccount);

            jGnashAccountEntries.put(this.id.id, newAccount);
        }

        for (Map.Entry<String, AccountImportEntry> entry : this.childAccountEntries.entrySet()) {
            if (!entry.getValue().createJGnashAccounts(contentHandler, engine, jGnashAccountEntries, accountIdsToIgnore)) {
                return false;
            }
        }
        
        contentHandler.updateStatusCallback(1, null);

        return true;
    }

    
    boolean setupAccountSecurities(Account account, GnuCashToJGnashContentHandler contentHandler) {
        SecurityNode securityNode = contentHandler.jGnashSecurities.get(this.commodityRef.id);
        if (securityNode == null) {
            CurrencyNode currencyNode = contentHandler.jGnashCurrencies.get(this.commodityRef.id);
            if (currencyNode == null) {
                contentHandler.recordWarning(this, "Message.Warning.StockAccountSecurityNotFound", 
                        this.commodityRef.id);
                return true;
            }
        }
        
        contentHandler.jGnashSecuritiesByStockAccountId.put(this.id.id, securityNode);
        
        account.addSecurity(securityNode);
        return true;
    }

    
    CurrencyNode getCurrencyNode(Engine engine) {
        CurrencyNode currencyNode = engine.getDefaultCurrency();
        return currencyNode;
    }
}
