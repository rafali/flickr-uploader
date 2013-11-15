package com.rafali.flickruploader;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.jdo.PersistenceManager;
import javax.jdo.Query;
import javax.persistence.EntityExistsException;
import javax.persistence.EntityNotFoundException;

import com.google.api.server.spi.config.Api;
import com.google.api.server.spi.config.ApiMethod;
import com.google.api.server.spi.config.ApiNamespace;
import com.google.api.server.spi.response.CollectionResponse;
import com.google.appengine.api.datastore.Cursor;
import com.google.appengine.datanucleus.query.JDOCursorHelper;

@Api(name = "appinstallendpoint", namespace = @ApiNamespace(ownerDomain = "rafali.com", ownerName = "rafali.com", packagePath = "flickruploader"))
public class AppInstallEndpoint {

	/**
	 * This method lists all the entities inserted in datastore. It uses HTTP GET method and paging support.
	 * 
	 * @return A CollectionResponse class containing the list of all entities persisted and a cursor to the next page.
	 */
	@SuppressWarnings({ "unchecked", "unused" })
	@ApiMethod(name = "listAppInstall")
	public CollectionResponse<AppInstall> listAppInstall(@Nullable @Named("cursor") String cursorString, @Nullable @Named("limit") Integer limit) {
		System.out.println("limit : " + limit);
		PersistenceManager mgr = null;
		Cursor cursor = null;
		List<AppInstall> execute = null;

		try {
			mgr = getPersistenceManager();
			Query query = mgr.newQuery(AppInstall.class);
			if (cursorString != null && cursorString != "") {
				cursor = Cursor.fromWebSafeString(cursorString);
				HashMap<String, Object> extensionMap = new HashMap<String, Object>();
				extensionMap.put(JDOCursorHelper.CURSOR_EXTENSION, cursor);
				query.setExtensions(extensionMap);
			}

			if (limit != null) {
				query.setRange(0, limit);
			}

			execute = (List<AppInstall>) query.execute();
			cursor = JDOCursorHelper.getCursor(execute);
			if (cursor != null)
				cursorString = cursor.toWebSafeString();

			// Tight loop for fetching all entities from datastore and accomodate
			// for lazy fetch.
			for (AppInstall obj : execute)
				;
		} finally {
			mgr.close();
		}

		return CollectionResponse.<AppInstall> builder().setItems(execute).setNextPageToken(cursorString).build();
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 * 
	 * @param id
	 *            the primary key of the java bean.
	 * @return The entity with primary key id.
	 */
	@ApiMethod(name = "getAppInstall")
	public AppInstall getAppInstall(@Named("id") String id) {
		PersistenceManager mgr = getPersistenceManager();
		AppInstall appinstall = null;
		try {
			appinstall = mgr.getObjectById(AppInstall.class, id);
		} finally {
			mgr.close();
		}
		return appinstall;
	}

	/**
	 * This method gets the entity having primary key id. It uses HTTP GET method.
	 * 
	 * @param emails
	 * @return list of AppInstall
	 */
	@ApiMethod(name = "getAppInstallsByEmails")
	public CollectionResponse<AppInstall> getAppInstallsByEmails(@Named("concatenatedEmails") String concatenatedEmails) {
		System.out.println("getAppInstallsByEmails : " + concatenatedEmails);
		String[] emails = concatenatedEmails.split(",");
		Set<AppInstall> appInstalls = new HashSet<AppInstall>();
		PersistenceManager mgr = getPersistenceManager();
		try {
			Query query = mgr.newQuery(AppInstall.class);
			query.setFilter("emails == :param");
			for (String email : emails) {
				List<AppInstall> result = (List<AppInstall>) query.execute(email);
				System.out.println(email + " : " + result);
				appInstalls.addAll(result);
			}
		} finally {
			mgr.close();
		}
		return CollectionResponse.<AppInstall> builder().setItems(appInstalls).build();
	}

	/**
	 * This inserts a new entity into App Engine datastore. If the entity already exists in the datastore, an exception is thrown. It uses HTTP POST method.
	 * 
	 * @param appinstall
	 *            the entity to be inserted.
	 * @return The inserted entity.
	 */
	@ApiMethod(name = "insertAppInstall")
	public AppInstall insertAppInstall(AppInstall appinstall) {
		PersistenceManager mgr = getPersistenceManager();
		try {
			if (containsAppInstall(appinstall)) {
				throw new EntityExistsException("Object already exists");
			}
			mgr.makePersistent(appinstall);
		} finally {
			mgr.close();
		}
		return appinstall;
	}

	/**
	 * This method is used for updating an existing entity. If the entity does not exist in the datastore, an exception is thrown. It uses HTTP PUT method.
	 * 
	 * @param appinstall
	 *            the entity to be updated.
	 * @return The updated entity.
	 */
	@ApiMethod(name = "updateAppInstall")
	public AppInstall updateAppInstall(AppInstall appinstall) {
		PersistenceManager mgr = getPersistenceManager();
		try {
			if (!containsAppInstall(appinstall)) {
				throw new EntityNotFoundException("Object does not exist");
			}
			mgr.makePersistent(appinstall);
		} finally {
			mgr.close();
		}
		return appinstall;
	}

	/**
	 * This method removes the entity with primary key id. It uses HTTP DELETE method.
	 * 
	 * @param id
	 *            the primary key of the entity to be deleted.
	 * @return The deleted entity.
	 */
	@ApiMethod(name = "removeAppInstall")
	public AppInstall removeAppInstall(@Named("id") String id) {
		PersistenceManager mgr = getPersistenceManager();
		AppInstall appinstall = null;
		try {
			appinstall = mgr.getObjectById(AppInstall.class, id);
			mgr.deletePersistent(appinstall);
		} finally {
			mgr.close();
		}
		return appinstall;
	}

	private boolean containsAppInstall(AppInstall appinstall) {
		PersistenceManager mgr = getPersistenceManager();
		boolean contains = true;
		try {
			mgr.getObjectById(AppInstall.class, appinstall.getDeviceId());
		} catch (javax.jdo.JDOObjectNotFoundException ex) {
			contains = false;
		} finally {
			mgr.close();
		}
		return contains;
	}

	private static PersistenceManager getPersistenceManager() {
		return PMF.get().getPersistenceManager();
	}

}
