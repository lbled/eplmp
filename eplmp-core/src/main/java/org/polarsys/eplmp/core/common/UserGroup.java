/*******************************************************************************
  * Copyright (c) 2017 DocDoku.
  * All rights reserved. This program and the accompanying materials
  * are made available under the terms of the Eclipse Public License v1.0
  * which accompanies this distribution, and is available at
  * http://www.eclipse.org/legal/epl-v10.html
  *
  * Contributors:
  *    DocDoku - initial API and implementation
  *******************************************************************************/

package org.polarsys.eplmp.core.common;

import javax.persistence.*;
import java.io.Serializable;
import java.util.HashSet;
import java.util.Set;

/**
 * Class which gathers users in a workspace context.
 * 
 * @author Florent Garin
 * @version 1.1, 8/07/09
 * @since V1.1
 */
@Table(name="USERGROUP")
@javax.persistence.IdClass(org.polarsys.eplmp.core.common.UserGroupKey.class)
@javax.persistence.Entity
@NamedQueries({
        @NamedQuery(name="UserGroup.findUserGroups", query="SELECT u FROM UserGroup u WHERE :user member of u.users AND u.workspaceId = :workspaceId")
})
public class UserGroup implements Serializable {

    @Column(length=100)
    @javax.persistence.Id
    private String id="";

    @javax.persistence.Column(name = "WORKSPACE_ID", length=100, nullable = false, insertable = false, updatable = false)
    @javax.persistence.Id
    private String workspaceId="";

    @javax.persistence.ManyToOne(optional=false, fetch=FetchType.EAGER)
    private Workspace workspace;

    @ManyToMany(fetch=FetchType.EAGER)
    @JoinTable(name="USERGROUP_USER",
    inverseJoinColumns={
        @JoinColumn(name="USER_LOGIN", referencedColumnName="LOGIN"),
       @JoinColumn(name="USER_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    },
    joinColumns={
        @JoinColumn(name="USERGROUP_ID", referencedColumnName="ID"),
        @JoinColumn(name="USERGROUP_ID_WORKSPACE_ID", referencedColumnName="WORKSPACE_ID")
    })
    private Set<User> users=new HashSet<>();

    public UserGroup(){

    }

    public UserGroup(Workspace pWorkspace, String pId) {
        setWorkspace(pWorkspace);
        id=pId;
    }

    public void setWorkspace(Workspace pWorkspace){
        workspace=pWorkspace;
        workspaceId=workspace.getId();
    }
    
    public Set<User> getUsers() {
        return users;
    }

    public boolean addUser(User pUser){
        return users.add(pUser);
    }

    public boolean removeUser(User pUser){
        return users.remove(pUser);
    }

    public boolean isMember(User user){
        return users.contains(user);
    }
    public String getId() {
        return id;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setUsers(Set<User> users) {
        this.users = users;
    }

    @Override
    public String toString() {
        return getId();
    }
}
