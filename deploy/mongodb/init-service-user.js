const database = process.env.MONGODB_DATABASE || "service";
const username = process.env.MONGODB_USERNAME;
const password = process.env.MONGODB_PASSWORD;

if (!username || !password) {
  throw new Error("MONGODB_USERNAME and MONGODB_PASSWORD are required");
}

db.getSiblingDB(database).createUser({
  user: username,
  pwd: password,
  roles: [{ role: "readWrite", db: database }],
});
