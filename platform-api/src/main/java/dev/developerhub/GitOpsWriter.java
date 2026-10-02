package dev.developerhub;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class GitOpsWriter {
  @Value("${developerhub.gitops-repo:hub-gitops}") private String gitopsRepo;
  private final GitHubClient github;
  public GitOpsWriter(GitHubClient github) { this.github=github; }
  public String write(String name,String owner,String env,String tag,String database,boolean observability,String domain) {
    String login=github.login();
    if(!github.repoExists(login,gitopsRepo)) {
      github.createRepoAndPush(gitopsRepo,"GitOps state managed by DeveloperHub",Map.of("README.md","# DeveloperHub GitOps\n","apps/dev/.gitkeep","","apps/staging/.gitkeep","","apps/prod/.gitkeep",""));
    }
    String path="apps/"+env+"/"+name+".yaml";
    String manifest=application(name,owner,env,login,tag,database,observability,domain);
    try {
      var existing=github.call("GET","/repos/"+login+"/"+gitopsRepo+"/contents/"+path,null);
      String prior=new String(java.util.Base64.getMimeDecoder().decode(existing.path("content").asText()),java.nio.charset.StandardCharsets.UTF_8);
      org.yaml.snakeyaml.Yaml yaml=new org.yaml.snakeyaml.Yaml();
      Map<String,Object> oldApp=yaml.load(prior),newApp=yaml.load(manifest);
      Map<String,Object> oldValues=values(oldApp),newValues=values(newApp);
      Map<String,Object> oldDb=(Map<String,Object>)oldValues.get("database"),newDb=(Map<String,Object>)newValues.get("database");
      for(String field:java.util.List.of("remoteKey","host","port","name")) if(oldDb.containsKey(field)) newDb.put(field,oldDb.get(field));
      manifest=yaml.dump(newApp);
    } catch(GitHubClient.GitHubException e) { if(e.status!=404) throw e; }
    github.upsertFile(login,gitopsRepo,path,manifest,"Set "+name+" "+env+" image "+tag);
    return "https://github.com/"+login+"/"+gitopsRepo+"/blob/main/"+path;
  }
  @SuppressWarnings("unchecked")
  private static Map<String,Object> values(Map<String,Object> app) {
    Map<String,Object> spec=(Map<String,Object>)app.get("spec");
    Map<String,Object> source=(Map<String,Object>)spec.get("source");
    Map<String,Object> helm=(Map<String,Object>)source.get("helm");
    return (Map<String,Object>)helm.get("valuesObject");
  }
  public void disable(String login,String name,String env) {
    String path="/repos/"+login+"/"+gitopsRepo+"/contents/apps/"+env+"/"+name+".yaml";
    String sha;
    try { sha=github.call("GET",path,null).path("sha").asText(); }
    catch(GitHubClient.GitHubException e) { if(e.status==404) return; throw e; }
    github.call("DELETE",path,Map.of("message","Approved dev cleanup for "+name,"sha",sha));
  }
  static String application(String name,String owner,String env,String login,String tag,String database,boolean observability,String domain) {
    return """
        apiVersion: argoproj.io/v1alpha1
        kind: Application
        metadata:
          name: %1$s-%3$s
          namespace: argocd
          finalizers:
            - resources-finalizer.argocd.argoproj.io
          labels:
            app.kubernetes.io/managed-by: developerhub
            developerhub/owner: %2$s
        spec:
          project: developerhub
          source:
            repoURL: https://github.com/%4$s/%1$s
            targetRevision: main
            path: helm
            helm:
              valuesObject:
                image:
                  repository: ghcr.io/%5$s/%1$s
                  tag: "%6$s"
                ingress:
                  enabled: true
                  host: %1$s-%3$s.%9$s
                database:
                  enabled: %7$s
                  secretName: %1$s-db
                  remoteKey: %1$s/%3$s/db
                observability:
                  enabled: %8$s
                imagePullSecrets:
                  - name: ghcr-pull
                registrySecret:
                  enabled: true
                  storeName: developerhub-aws
                  remoteKey: developerhub/ghcr
          destination:
            server: https://kubernetes.default.svc
            namespace: %2$s-%3$s
          syncPolicy:
        %10$s
            syncOptions:
              - CreateNamespace=true
        """.formatted(name,owner,env,login,login.toLowerCase(),tag,database.equals("PostgreSQL"),observability,domain,tag.equals("pending") ? "" : "    automated:\n      prune: true\n      selfHeal: true");
  }
}




