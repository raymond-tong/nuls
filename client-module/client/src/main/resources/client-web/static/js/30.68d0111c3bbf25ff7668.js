webpackJsonp([30],{L1Au:function(e,t){},OKVl:function(e,t,s){"use strict";Object.defineProperty(t,"__esModule",{value:!0});var o=s("LPk9"),n=s("FJop"),a=s("2tLR"),i=s("YgNb"),c={data:function(){return{fellPathSetInterval:null,encrypted:!1,imageUrl:"",keyStorePath:"",keystoreInfo:"",importAccountLoading:!1}},components:{Back:o.a,Password:n.a},mounted:function(){},methods:{keystore:function(){var e=this,t=document.getElementById("fileId");t.click(),t.onchange=function(){if(""!==this.value){var s=t.files[0],o=s.name.toLowerCase().split(".").splice(-1);console.log(s),console.log(s.name),console.log(o)}else e.$message({type:"warning",message:e.$t("message.c194"),duration:"2000"})}},readFiles:function(e){var t=this;if(window.FileReader){var s=e.files[0],o=(s.name.split(".")[0],new FileReader);o.onload=function(){t.keystoreInfo=this.result},o.readAsText(s)}else if(void 0!==window.ActiveXObject){var n=void 0;(n=new ActiveXObject("Microsoft.XMLDOM")).async=!1,n.load(e.value),t.keystoreInfo=n.xml}else if(document.implementation&&document.implementation.createDocument){var a=void 0;(a=document.implementation.createDocument("","",null)).async=!1,a.load(e.value),t.keystoreInfo=a.xml}else alert("error")},toClose:function(e){e||(document.getElementById("fileId").value="")},toSubmit:function(e){var t={accountKeyStoreDto:JSON.parse(this.keystoreInfo),password:e,overwrite:!0};this.postKeyStore(t)},postKeyStore:function(e){var t=this;this.importAccountLoading=!0,Object(a.i)(e).then(function(e){e.success?(localStorage.setItem("newAccountAddress",e.data.value),localStorage.setItem("addressRemark",""),Object(a.b)(e.data.value).then(function(e){e.success&&localStorage.setItem("addressAlias",e.data.alias)}),localStorage.setItem("encrypted",t.encrypted.toString()),t.getAccountList(),t.$message({type:"success",message:t.$t("message.passWordSuccess")})):t.$message({type:"warning",message:t.$t("message.passWordFailed")+e.data.msg}),t.importAccountLoading=!1}).catch(function(e){t.getAccountList(),t.$message({type:"success",message:t.$t("message.c197"),duration:"3000"}),t.importAccountLoading=!1})},getAccountList:function(){var e=this;Object(i.f)().then(function(t){t.success&&(e.$store.commit("setAddressList",t.list),1===t.list.length?e.$router.push({name:"/wallet"}):e.$router.push({name:"/userInfo"}))})},importKey:function(){this.$router.push({path:"/firstInto/firstInfo/importKey"})}}},r={render:function(){var e=this,t=e.$createElement,s=e._self._c||t;return s("div",{directives:[{name:"loading",rawName:"v-loading",value:e.importAccountLoading,expression:"importAccountLoading"}],staticClass:"import-account"},[s("Back",{attrs:{backTitle:this.$t("message.firstInfoTitle")}}),e._v(" "),s("h1",[e._v(e._s(e.$t("message.inportAccount")))]),e._v(" "),s("input",{staticClass:"hidden",attrs:{type:"file",id:"fileId"}}),e._v(" "),s("p",{staticClass:"hidden",attrs:{id:"preview",value:""}}),e._v(" "),s("div",{staticClass:"keystore",on:{click:e.keystore}},[s("h1",[e._v(e._s(e.$t("message.c189")))]),e._v(" "),s("p",[e._v(e._s(e.$t("message.c190"))),s("br"),e._v(e._s(e.$t("message.c191")))]),e._v(" "),s("h3",{directives:[{name:"show",rawName:"v-show",value:!1,expression:"false"}]},[e._v("\n      "+e._s(e.$t("message.c192"))+"\n    ")])])],1)},staticRenderFns:[]};var l=s("vSla")(c,r,!1,function(e){s("L1Au")},null,null);t.default=l.exports}});