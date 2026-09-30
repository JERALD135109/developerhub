create table team(id serial primary key, name text, owner_email text, cost_center text);
create table service(id serial primary key, name text, repo_url text, owner_team int references team(id), lifecycle text, system text);
create table environment(service_id int references service(id), env text, cluster text, namespace text, url text, status text);
create table provision_request(id serial primary key, type text, requested_by text, status text, created_at timestamp);
create table audit_event(actor text, action text, resource text, result text, timestamp timestamp);
create table scorecard_result(service_id int references service(id), rule text, status text, details text);
