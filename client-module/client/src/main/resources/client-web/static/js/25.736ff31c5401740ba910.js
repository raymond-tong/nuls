webpackJsonp([25],{StxY:function(t,e,a){"use strict";Object.defineProperty(e,"__esModule",{value:!0});var s=a("LPk9"),n=a("6ROu"),l=a.n(n),o=a("x47x"),i=a("YgNb"),r={data:function(){return{pledgeData:[],total:0}},components:{Back:s.a},mounted:function(){""!==localStorage.getItem("newAccountAddress")&&this.getConsensusDeposit("/consensus/deposit/address/"+localStorage.getItem("newAccountAddress"),{pageSize:"20",pageNumber:1})},methods:{getConsensusDeposit:function(t,e){var a=this;this.$fetch(t,e).then(function(t){if(t.success){var e=new o.BigNumber(1e-8);a.total=t.data.total;for(var s=0;s<t.data.list.length;s++)t.data.list[s].deposit=parseFloat(e.times(t.data.list[s].deposit).toString()),t.data.list[s].time=l()(Object(i.c)(t.data.list[s].time)).format("YYYY-MM-DD HH:mm:ss");a.pledgeData=t.data.list}})},pledgeSize:function(t){this.getConsensusDeposit("/consensus/deposit/address/"+localStorage.getItem("newAccountAddress"),{pageNumber:t,pageSize:"20"})},handleClick:function(t){this.$router.push({name:"/myNode",query:{agentAddress:t.agentHash,agentHash:t.agentHash}})}}},c={render:function(){var t=this,e=t.$createElement,a=t._self._c||e;return a("div",{staticClass:"pledge-info"},[a("Back",{attrs:{backTitle:this.$t("message.consensusManagement")}}),t._v(" "),a("h2",[t._v(t._s(t.$t("message.c48")))]),t._v(" "),a("el-table",{attrs:{data:t.pledgeData}},[a("el-table-column",{attrs:{prop:"agentName",label:t.$t("message.c24"),"min-width":"120",align:"center"}}),t._v(" "),a("el-table-column",{attrs:{prop:"deposit",label:t.$t("message.amount"),"min-width":"210",align:"center"}}),t._v(" "),a("el-table-column",{attrs:{prop:"status",label:t.$t("message.state"),width:"100",align:"center"},scopedSlots:t._u([{key:"default",fn:function(e){return[t._v("\n        "+t._s(t.$t("message.status"+e.row.status))+"\n      ")]}}])}),t._v(" "),a("el-table-column",{attrs:{prop:"time",label:t.$t("message.c49"),width:"160",align:"center"}}),t._v(" "),a("el-table-column",{attrs:{label:t.$t("message.operation"),width:"90",align:"center"},scopedSlots:t._u([{key:"default",fn:function(e){return[a("el-button",{attrs:{type:"text",size:"small"},on:{click:function(a){t.handleClick(e.row)}}},[t._v(t._s(t.$t("message.c50"))+"\n        ")])]}}])})],1),t._v(" "),a("el-pagination",{directives:[{name:"show",rawName:"v-show",value:t.totalOK=this.total>20,expression:"totalOK = this.total > 20 ? true:false"}],staticClass:"cb",attrs:{layout:"prev, pager, next","page-size":20,total:this.total},on:{"current-change":t.pledgeSize}})],1)},staticRenderFns:[]};var u=a("vSla")(r,c,!1,function(t){a("pgR0")},null,null);e.default=u.exports},pgR0:function(t,e){}});