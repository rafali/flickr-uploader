package com.rafali.flickruploader;

import java.util.Properties;

import javax.jdo.JDOHelper;
import javax.jdo.PersistenceManagerFactory;

public final class PMF {
    private static final PersistenceManagerFactory pmfInstance;
    static {
        Properties properties = new Properties();
        properties.setProperty("javax.jdo.PersistenceManagerFactoryClass",
                "org.datanucleus.store.appengine.jdo.DatastoreJDOPersistenceManagerFactory");
        properties.setProperty("javax.jdo.option.ConnectionURL", "appengine");
        properties.setProperty("javax.jdo.option.NontransactionalRead", "true");
        properties.setProperty("javax.jdo.option.NontransactionalWrite", "true");
        properties.setProperty("javax.jdo.option.RetainValues", "true");
        properties.setProperty("datanucleus.appengine.autoCreateDatastoreTxns", "true");
        properties.setProperty("datanucleus.appengine.ignorableMetaDataBehavior", "NONE");
        pmfInstance = JDOHelper.getPersistenceManagerFactory(properties);
    }

	private PMF() {
	}

	public static PersistenceManagerFactory get() {
		return pmfInstance;
	}
}