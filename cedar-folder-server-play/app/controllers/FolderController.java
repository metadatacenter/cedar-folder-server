package controllers;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.metadatacenter.constant.HttpConstants;
import org.metadatacenter.model.folderserver.CedarFSFolder;
import org.metadatacenter.model.folderserver.CedarFSNode;
import org.metadatacenter.server.neo4j.Neo4JUserSession;
import org.metadatacenter.server.neo4j.NodeExtraParameter;
import org.metadatacenter.server.neo4j.NodeLabel;
import org.metadatacenter.server.security.Authorization;
import org.metadatacenter.server.security.CedarAuthFromRequestFactory;
import org.metadatacenter.server.security.exception.CedarAccessException;
import org.metadatacenter.server.security.model.IAuthRequest;
import org.metadatacenter.server.security.model.auth.CedarPermission;
import org.metadatacenter.server.security.model.user.CedarUser;
import org.metadatacenter.server.util.ParameterUtil;
import org.metadatacenter.util.json.JsonMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import play.mvc.Result;
import utils.DataServices;

import java.util.HashMap;
import java.util.Map;

public class FolderController extends AbstractFolderServerController {
  private static Logger log = LoggerFactory.getLogger(FolderController.class);

  public static Result createFolder() {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.FOLDER_CREATE);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while creating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode creationRequest = request().body().asJson();
      if (creationRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      String folderId = ParameterUtil.getString(creationRequest, "folderId", "");
      String path = ParameterUtil.getString(creationRequest, "path", "");
      if (folderId.isEmpty() && path.isEmpty()) {
        return badRequest(generateErrorDescription("parentFolderNotSpecified",
            "You need to supply either path or folderId parameter identifying the parent folder"));

      }
      if (!folderId.isEmpty() && !path.isEmpty()) {
        return badRequest(generateErrorDescription("parentFolderSpecifiedTwice",
            "You need to supply either path or folderId parameter (not both) identifying the parent folder"));
      }

      CedarFSFolder parentFolder = null;

      String normalizedPath = null;
      if (!path.isEmpty()) {
        normalizedPath = neoSession.normalizePath(path);
        if (!normalizedPath.equals(path)) {
          throw new IllegalArgumentException("You must supply the path of the new folder in normalized form!");
        }
        parentFolder = neoSession.findFolderByPath(path);
      }

      if (!folderId.isEmpty()) {
        parentFolder = neoSession.findFolderById(folderId);
      }

      if (parentFolder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        errorParams.put("folderId", folderId);
        return badRequest(generateErrorDescription("parentNotPresent",
            "The parent folder is not present!", errorParams));
      }

      // get name parameter
      String name = ParameterUtil.getStringOrThrowError(creationRequest, "name",
          "You must supply the name of the new folder!");
      // test new folder name syntax
      String normalizedName = neoSession.sanitizeName(name);
      if (!normalizedName.equals(name)) {
        throw new IllegalArgumentException("The new folder name contains invalid characters!");
      }

      // get description parameter
      String description = ParameterUtil.getStringOrThrowError(creationRequest, "description",
          "You must supply the description of the new folder!");

      // check existence of parent folder
      CedarFSFolder newFolder = null;
      CedarFSNode newFolderCandidate = neoSession.findNodeByParentIdAndName(parentFolder, name);
      if (newFolderCandidate != null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("parentFolderId", parentFolder.getId());
        errorParams.put("name", name);
        return badRequest(generateErrorDescription("nodeAlreadyPresent",
            "There is already a node with the same name at the requested location!", errorParams));
      }

      Map<NodeExtraParameter, Object> extraProperties = new HashMap<>();
      extraProperties.put(NodeExtraParameter.IS_PUBLICLY_READABLE, parentFolder.isPubliclyReadable());

      newFolder = neoSession.createFolderAsChildOfId(parentFolder.getId(), name, description, NodeLabel.FOLDER,
          extraProperties);

      if (newFolder != null) {
        JsonNode createdFolder = JsonMapper.MAPPER.valueToTree(newFolder);
        String absoluteUrl = routes.FolderController.findFolder(newFolder.getId()).absoluteURL(request());
        response().setHeader(HttpConstants.HTTP_HEADER_LOCATION, absoluteUrl);
        return created(createdFolder);
      } else {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("path", path);
        errorParams.put("parentFolderId", parentFolder.getId());
        errorParams.put("name", name);
        return badRequest(generateErrorDescription("folderNotCreated",
            "The folder was not created!", errorParams));
      }
    } catch (IllegalArgumentException e) {
      play.Logger.error("Illegal argument while creating the folder", e);
      return badRequestWithError(e);
    } catch (Exception e) {
      play.Logger.error("Error while creating the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result findFolder(String folderId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.FOLDER_READ);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while reading the folder", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      CedarFSFolder folder = neoSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {

        neoSession.addPathAndParentId(folder);
        JsonNode folderNode = JsonMapper.MAPPER.valueToTree(folder);
        return ok(folderNode);
      }
    } catch (Exception e) {
      play.Logger.error("Error while getting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result updateFolder(String folderId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.FOLDER_UPDATE);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while updating the folder", e);
      return forbiddenWithError(e);
    }

    try {
      JsonNode folderUpdateRequest = request().body().asJson();
      if (folderUpdateRequest == null) {
        throw new IllegalArgumentException("You must supply the request body as a json object!");
      }

      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      String name = null;
      JsonNode nameNode = folderUpdateRequest.get("name");
      if (nameNode != null) {
        name = nameNode.asText();
        if (name != null) {
          name = name.trim();
        }
      }

      // test update folder name syntax
      if (name != null) {
        String normalizedName = neoSession.sanitizeName(name);
        if (!normalizedName.equals(name)) {
          throw new IllegalArgumentException("The folder name contains invalid characters!");
        }
      }

      String description = null;
      JsonNode descriptionNode = folderUpdateRequest.get("description");
      if (descriptionNode != null) {
        description = descriptionNode.asText();
        if (description != null) {
          description = description.trim();
        }
      }

      if ((name == null || name.isEmpty()) && (description == null || description.isEmpty())) {
        throw new IllegalArgumentException("You must supply the new description or the new name of the folder!");
      }

      CedarFSFolder folder = neoSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        Map<String, String> updateFields = new HashMap<>();
        if (description != null) {
          updateFields.put("description", description);
        }
        if (name != null) {
          updateFields.put("name", name);
        }
        CedarFSFolder updatedFolder = neoSession.updateFolderById(folderId, updateFields);
        if (updatedFolder == null) {
          return notFound();
        } else {
          JsonNode updatedFolderNode = JsonMapper.MAPPER.valueToTree(updatedFolder);
          return ok(updatedFolderNode);
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while updating the folder", e);
      return internalServerErrorWithError(e);
    }
  }

  public static Result deleteFolder(String folderId) {
    IAuthRequest frontendRequest = null;
    CedarUser currentUser = null;
    try {
      frontendRequest = CedarAuthFromRequestFactory.fromRequest(request());
      currentUser = Authorization.getUserAndEnsurePermission(frontendRequest, CedarPermission.FOLDER_DELETE);
    } catch (CedarAccessException e) {
      play.Logger.error("Access Error while deleting the folder", e);
      return forbiddenWithError(e);
    }

    try {
      Neo4JUserSession neoSession = DataServices.getInstance().getNeo4JSession(currentUser);

      CedarFSFolder folder = neoSession.findFolderById(folderId);
      if (folder == null) {
        ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
        errorParams.put("id", folderId);
        return notFound(generateErrorDescription("folderNotFound",
            "The folder can not be found by id:" + folderId, errorParams));
      } else {
        if (folder.isSystem()) {
          ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
          errorParams.put("id", folderId);
          errorParams.put("folderType", "system");
          return badRequest(generateErrorDescription("folderCanNotBeDeleted",
              "System folders can not be deleted", errorParams));
        } else {
          long contentCount = neoSession.findFolderContentsCount(folder.getId());
          if (contentCount > 0) {
            ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
            errorParams.put("id", folderId);
            errorParams.put("count", "contentCount");
            return badRequest(generateErrorDescription("folderCanNotBeDeleted",
                "Non-empty folders can not be deleted", errorParams));
          }
          boolean deleted = neoSession.deleteFolderById(folderId);
          if (deleted) {
            return noContent();
          } else {
            // TODO: check folder contents, if not, delete only if "?force=true" param is present
            ObjectNode errorParams = JsonNodeFactory.instance.objectNode();
            errorParams.put("id", folderId);
            return internalServerErrorWithError(generateErrorDescription("folderNotDeleted",
                "The folder can not be delete by id:" + folderId, errorParams));
          }
        }
      }
    } catch (Exception e) {
      play.Logger.error("Error while deleting the folder", e);
      return internalServerErrorWithError(e);
    }
  }

}
