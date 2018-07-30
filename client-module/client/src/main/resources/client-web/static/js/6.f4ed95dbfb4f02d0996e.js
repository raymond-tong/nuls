webpackJsonp([6],{"7wgv":function(s,e,t){"use strict";var a={data:function(){var s=this;return{passVisible:!1,passForm:{pass:"",checkPass:""},rulesPass:{pass:[{validator:function(e,t,a){""===t?a(new Error(s.$t("message.walletPassWord1"))):/(?!^((\d+)|([a-zA-Z]+)|([~!@#\$%\^&\*\(\)]+))$)^[a-zA-Z0-9~!@#\$%\^&\*\(\)]{8,21}$/.exec(t)?(""!==s.passForm.checkPass&&s.$refs.passForm.validateField("checkPass"),a()):a(new Error(s.$t("message.walletPassWord1")))},trigger:"blur"}],checkPass:[{validator:function(e,t,a){""===t?a(new Error(s.$t("message.affirmWalletPassWordEmpty"))):t!==s.passForm.pass?a(new Error(s.$t("message.passWordAtypism"))):a()},trigger:"blur"}]}}},created:function(){},methods:{passwordShow:function(){},passwordClose:function(){},showPasswordTwo:function(s){this.passForm.password="",this.passVisible=s},submitForm:function(s){var e=this;this.$refs[s].validate(function(s){if(!s)return!1;e.$emit("toSubmit",e.passForm.checkPass),e.passVisible=!1})},noPassword:function(){this.passForm.checkPass="",this.$emit("toSubmit",this.passForm.checkPass),this.passVisible=!1}}},o={render:function(){var s=this,e=s.$createElement,t=s._self._c||e;return t("el-dialog",{staticClass:"password-two-dialog",attrs:{title:"",visible:s.passVisible,top:"15vh"},on:{"update:visible":function(e){s.passVisible=e},open:s.passwordShow,close:s.passwordClose}},[t("h2",[s._v(s._s(s.$t("message.setPassWord")))]),s._v(" "),t("el-form",{ref:"passForm",staticClass:"set-pass",attrs:{model:s.passForm,"status-icon":"",rules:s.rulesPass}},[t("el-form-item",{staticStyle:{"margin-bottom":"5px"},attrs:{label:s.$t("message.walletPassWord"),prop:"pass"}},[t("el-input",{attrs:{type:"password",maxlength:20,placeholder:this.$t("message.walletPassWord1")},model:{value:s.passForm.pass,callback:function(e){s.$set(s.passForm,"pass",e)},expression:"passForm.pass"}})],1),s._v(" "),t("el-form-item",{staticStyle:{"margin-bottom":"5px"},attrs:{label:s.$t("message.affirmWalletPassWord"),prop:"checkPass"}},[t("el-input",{attrs:{type:"password",maxlength:20,placeholder:this.$t("message.affirmWalletPassWordEmpty")},model:{value:s.passForm.checkPass,callback:function(e){s.$set(s.passForm,"checkPass",e)},expression:"passForm.checkPass"}})],1),s._v(" "),t("div",{staticClass:"set-pass-title"},[s._v(s._s(s.$t("message.passWordInfo")))]),s._v(" "),t("el-form-item",[t("el-button",{staticClass:"set-pass-submit",attrs:{type:"primary",id:"setPassTwo"},on:{click:function(e){s.submitForm("passForm")}}},[s._v("\n                "+s._s(s.$t("message.passWordAffirm"))+"\n            ")]),s._v(" "),t("div",{staticClass:"new-no-pass",on:{click:s.noPassword}},[s._v("\n               "+s._s(s.$t("message.c159"))+"\n           ")])],1)],1)],1)},staticRenderFns:[]};var r=t("vSla")(a,o,!1,function(s){t("8q5C")},null,null);e.a=r.exports},"8q5C":function(s,e){},"9lt0":function(s,e){},sz9L:function(s,e,t){"use strict";Object.defineProperty(e,"__esModule",{value:!0});var a=t("LPk9"),o=t("7wgv"),r={data:function(){return{submitId:"importKey",encrypted:!1,keyData:{keyInfo:""},keyRules:{keyInfo:[{required:!0,message:this.$t("message.keyLow"),trigger:"blur"}]},importKeyLoading:!1}},components:{Back:a.a,PasswordTow:o.a},methods:{keySubmit:function(s){var e=this;this.$refs[s].validate(function(s){if(!s)return console.log("error submit!!"),!1;e.$refs.passTwo.showPasswordTwo(!0)})},toSubmit:function(s){var e=this;this.importKeyLoading=!0;var t="";""===s?t='{"priKey":"'+this.keyData.keyInfo+'","password":""}':(t='{"priKey":"'+this.keyData.keyInfo+'","password":"'+s+'"}',this.encrypted=!0),this.$post("/account/import/pri",t).then(function(s){s.success?(localStorage.setItem("newAccountAddress",s.data.value),localStorage.setItem("addressAlias",""),localStorage.setItem("encrypted",e.encrypted.toString()),e.getAccountList("/account"),e.$message({type:"success",message:e.$t("message.passWordSuccess")})):e.$message({type:"warning",message:e.$t("message.passWordFailed")+s.data.msg}),e.importKeyLoading=!1,e.passwordVisible=!1}).catch(function(s){e.getAccountList("/account"),e.$message({type:"success",message:e.$t("message.c197"),duration:"3000"}),e.importKeyLoading=!1})},getAccountList:function(s){var e=this;this.$fetch(s).then(function(s){s.success&&(e.$store.commit("setAddressList",s.data.list),1===s.data.list.length?(localStorage.setItem("newAccountAddress",s.data.list[0].address),localStorage.setItem("encrypted",s.data.list[0].encrypted),e.$router.push({name:"/wallet"})):e.$router.push({name:"/userInfo",params:{address:s.data}}))}).catch(function(s){console.log("User List err"+s)})}}},i={render:function(){var s=this,e=s.$createElement,t=s._self._c||e;return t("div",{directives:[{name:"loading",rawName:"v-loading",value:s.importKeyLoading,expression:"importKeyLoading"}],staticClass:"import-key"},[t("Back",{attrs:{backTitle:this.$t("message.inportAccount")}}),s._v(" "),t("h2",[s._v(s._s(s.$t("message.key")))]),s._v(" "),t("el-form",{ref:"keyData",attrs:{model:s.keyData,rules:s.keyRules,"label-position":"top"}},[t("el-form-item",{attrs:{label:s.$t("message.keyLow"),prop:"keyInfo"}},[t("el-input",{attrs:{type:"textarea",maxlength:100},model:{value:s.keyData.keyInfo,callback:function(e){s.$set(s.keyData,"keyInfo","string"==typeof e?e.trim():e)},expression:"keyData.keyInfo"}})],1),s._v(" "),t("el-form-item",[t("el-button",{attrs:{type:"primary",id:"importKey"},on:{click:function(e){s.keySubmit("keyData")}}},[s._v("\n                "+s._s(s.$t("message.confirmButtonText"))+"\n            ")])],1)],1),s._v(" "),t("PasswordTow",{ref:"passTwo",on:{toSubmit:s.toSubmit}})],1)},staticRenderFns:[]};var n=t("vSla")(r,i,!1,function(s){t("9lt0")},null,null);e.default=n.exports}});